package ca.uhn.fhir.jpa.starter.permission.service;

import ca.uhn.fhir.jpa.starter.permission.PermissionClaimUtils;
import ca.uhn.fhir.jpa.starter.permission.PermissionContextAttributes;
import ca.uhn.fhir.jpa.starter.permission.PermissionProperties;
import ca.uhn.fhir.jpa.starter.permission.PermissionRepositoryResponseException;
import ca.uhn.fhir.jpa.starter.permission.PermissionRepositoryUnavailableException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import com.fyrstain.fhir.security.core.model.PermissionContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Permission;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FhirRepositoryPermissionSource implements PermissionSource {

	private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

	private final PermissionProperties.Repository myRepositoryProperties;
	private final HttpClient myHttpClient;
	private final ca.uhn.fhir.parser.IParser myParser = ca.uhn.fhir.context.FhirContext.forR5().newJsonParser();

	public FhirRepositoryPermissionSource(PermissionProperties.Repository theRepositoryProperties) {
		myRepositoryProperties = theRepositoryProperties;
		myHttpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(Math.max(1, theRepositoryProperties.getTimeoutSeconds())))
				.build();
		myParser.setPrettyPrint(false);
	}

	@Override
	public List<IBaseResource> getPermissions(PermissionContext context) {
		String queryTemplate = myRepositoryProperties.getQueryTemplate();
		if (!StringUtils.hasText(queryTemplate)) {
			throw new InternalErrorException("Permission repository query template is not configured");
		}

		String requestUrl = buildRequestUrl(context, queryTemplate);
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(requestUrl))
				.GET()
				.timeout(Duration.ofSeconds(Math.max(1, myRepositoryProperties.getTimeoutSeconds())))
				.header("Accept", "application/fhir+json, application/json");

		String bearerToken = resolveBearerToken(context);
		if (StringUtils.hasText(bearerToken)) {
			requestBuilder.header("Authorization", "Bearer " + bearerToken);
		}

		HttpResponse<String> response;
		try {
			response = myHttpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new PermissionRepositoryUnavailableException("Permission repository is unavailable", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PermissionRepositoryUnavailableException("Permission repository call was interrupted", e);
		}

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new PermissionRepositoryResponseException(
					"Permission repository returned HTTP " + response.statusCode());
		}
		return parsePermissions(response.body());
	}

	private List<IBaseResource> parsePermissions(String body) {
		try {
			IBaseResource resource = myParser.parseResource(body);
			if (resource instanceof Permission permission) {
				return List.of(permission);
			}
			if (resource instanceof Bundle bundle) {
				List<IBaseResource> resources = new ArrayList<>();
				for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
					if (entry.getResource() instanceof Permission permission) {
						resources.add(permission);
					}
				}
				return resources;
			}
			throw new PermissionRepositoryResponseException("Permission repository did not return a Permission or Bundle payload");
		} catch (RuntimeException e) {
			throw new PermissionRepositoryResponseException("Unable to parse permission repository response", e);
		}
	}

	private String buildRequestUrl(PermissionContext context, String queryTemplate) {
		String resolvedTemplate = resolveTemplate(queryTemplate, context);
		if (resolvedTemplate.startsWith("http://") || resolvedTemplate.startsWith("https://")) {
			return resolvedTemplate;
		}

		String baseUrl = myRepositoryProperties.getBaseUrl();
		if (!StringUtils.hasText(baseUrl)) {
			Object serverBase = context.getAttribute(PermissionContextAttributes.SERVER_BASE_URL);
			baseUrl = serverBase != null ? serverBase.toString() : null;
		}
		if (!StringUtils.hasText(baseUrl)) {
			throw new InternalErrorException("Permission repository base URL is not configured");
		}

		String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		String normalizedQuery = resolvedTemplate.startsWith("/") ? resolvedTemplate.substring(1) : resolvedTemplate;
		return normalizedBaseUrl + "/" + normalizedQuery;
	}

	private String resolveTemplate(String template, PermissionContext context) {
		Matcher matcher = TEMPLATE_PATTERN.matcher(template);
		StringBuffer buffer = new StringBuffer();
		while (matcher.find()) {
			String replacement = resolvePlaceholder(matcher.group(1), context);
			matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(buffer);
		return buffer.toString();
	}

	private String resolvePlaceholder(String placeholder, PermissionContext context) {
		if ("userId".equals(placeholder)) {
			return safeValue(context.getUserId());
		}
		if ("displayName".equals(placeholder)) {
			return safeValue(context.getDisplayName());
		}
		if ("organizationId".equals(placeholder)) {
			return safeValue(context.getOrganizationId());
		}
		if ("token".equals(placeholder)) {
			return safeValue(context.getToken());
		}
		if (placeholder.startsWith("claim.")) {
			Object claimsObject = context.getAttribute(PermissionContextAttributes.CLAIMS);
			if (claimsObject instanceof Map<?, ?> claimsMap) {
				@SuppressWarnings("unchecked")
				Map<String, Object> claims = (Map<String, Object>) claimsMap;
				Object value = PermissionClaimUtils.getClaimValue(claims, placeholder.substring("claim.".length()));
				return flattenValue(value);
			}
		}
		return "";
	}

	private String flattenValue(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof String stringValue) {
			return safeValue(stringValue);
		}
		if (value instanceof Collection<?> collection) {
			List<String> values = new ArrayList<>();
			for (Object item : collection) {
				String flattened = flattenValue(item);
				if (!flattened.isBlank()) {
					values.add(flattened);
				}
			}
			return String.join(",", values);
		}
		return safeValue(String.valueOf(value));
	}

	private String resolveBearerToken(PermissionContext context) {
		if (StringUtils.hasText(myRepositoryProperties.getBearerToken())) {
			return myRepositoryProperties.getBearerToken().trim();
		}
		if (myRepositoryProperties.isReuseIncomingToken() && StringUtils.hasText(context.getToken())) {
			return context.getToken();
		}
		return null;
	}

	private String safeValue(String value) {
		return value != null ? URLEncoder.encode(value, StandardCharsets.UTF_8) : "";
	}
}
