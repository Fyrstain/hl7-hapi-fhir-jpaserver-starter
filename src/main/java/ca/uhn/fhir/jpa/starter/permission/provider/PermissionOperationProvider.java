package ca.uhn.fhir.jpa.starter.permission.provider;

import ca.uhn.fhir.jpa.starter.permission.PermissionContextFactory;
import ca.uhn.fhir.jpa.starter.permission.PermissionProperties;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import com.fyrstain.fhir.security.core.FhirAuthorizationEngine;
import com.fyrstain.fhir.security.core.PermissionEvaluator;
import com.fyrstain.fhir.security.core.PermissionService;
import com.fyrstain.fhir.security.core.model.FhirRequest;
import com.fyrstain.fhir.security.core.model.FhirResponse;
import com.fyrstain.fhir.security.core.model.PermissionContext;
import com.fyrstain.fhir.security.core.model.RequestEvaluationResult;
import com.fyrstain.fhir.security.core.r4.SimpleR4PermissionEvaluator;
import com.fyrstain.fhir.security.core.r5.SimpleR5PermissionEvaluator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r5.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PermissionOperationProvider {

	private static final Logger ourLog = LoggerFactory.getLogger(PermissionOperationProvider.class);

	private final PermissionService myPermissionService;
	private final PermissionProperties myPermissionProperties;
	private final PermissionContextFactory myPermissionContextFactory;

	public PermissionOperationProvider(
			PermissionService thePermissionService,
			PermissionProperties thePermissionProperties,
			PermissionContextFactory thePermissionContextFactory) {
		myPermissionService = thePermissionService;
		myPermissionProperties = thePermissionProperties;
		myPermissionContextFactory = thePermissionContextFactory;
	}

	@Operation(name = "$authorize-request")
	public Parameters authorizeRequest(@ResourceParam Parameters theParameters) {
		String fhirVersion = getFirstString(theParameters, "fhirVersion", myPermissionProperties.getDefaultFhirVersion());
		PermissionContext context = myPermissionContextFactory.fromParameters(theParameters);
		List<IBaseResource> inlinePermissions = parseInlinePermissions(theParameters);

		FhirRequest request = buildFhirRequest(theParameters, fhirVersion);
		Map<String, List<String>> originalSearchParameters =
				request.getSearchParameters() != null ? new LinkedHashMap<>(request.getSearchParameters()) : Collections.emptyMap();

		RequestEvaluationResult result = authorizationEngine(fhirVersion, inlinePermissions).evaluateRequest(context, request);
		boolean rewritten = result.isAllowed() && !Objects.equals(originalSearchParameters, result.getModifiedSearchParameters());

		if (!result.isAllowed() && myPermissionProperties.isFailClosed()) {
			throw new ForbiddenOperationException("Request is not authorized");
		}

		Parameters response = new Parameters();
		String decision = result.isAllowed() ? (rewritten ? "ALLOW_WITH_REWRITE" : "ALLOW") : "DENY";
		int httpStatus = result.isAllowed() ? 200 : 403;
		String reasonCode = result.isAllowed() ? (rewritten ? "SEARCH_SCOPE_RESTRICTED" : "AUTHORIZED") : "FORBIDDEN";

		response.addParameter().setName("decision").setValue(new CodeType(decision));
		response.addParameter().setName("allowed").setValue(new BooleanType(result.isAllowed()));
		response.addParameter().setName("httpStatus").setValue(new IntegerType(httpStatus));
		response.addParameter().setName("reasonCode").setValue(new CodeType(reasonCode));
		response.addParameter()
				.setName("rewrittenQueryString")
				.setValue(new StringType(toQueryString(result.getModifiedSearchParameters())));
		addQueryParameters(response, "rewrittenQueryParameter", result.getModifiedSearchParameters());
		for (String warning : result.getWarnings()) {
			response.addParameter().setName("warning").setValue(new StringType(warning));
		}
		return response;
	}

	@Operation(name = "$filter-response")
	public Parameters filterResponse(@ResourceParam Parameters theParameters) {
		String fhirVersion = getFirstString(theParameters, "fhirVersion", myPermissionProperties.getDefaultFhirVersion());
		PermissionContext context = myPermissionContextFactory.fromParameters(theParameters);
		List<IBaseResource> inlinePermissions = parseInlinePermissions(theParameters);

		String responseBodyJson = getRequiredString(theParameters, "responseBodyJson");
		int statusCode = getFirstInteger(theParameters, "statusCode", 200);
		IBaseResource resource = parsePayload(responseBodyJson, fhirVersion);
		String normalizedBefore = serializePayload(resource, fhirVersion);

		FhirResponse filteredResponse = authorizationEngine(fhirVersion, inlinePermissions)
				.filterResponse(context, new FhirResponse().setStatusCode(statusCode).setResource(resource));

		String filteredBodyJson = serializePayload(filteredResponse.getResource(), fhirVersion);
		boolean filtered = !Objects.equals(normalizedBefore, filteredBodyJson);

		Parameters response = new Parameters();
		response.addParameter().setName("httpStatus").setValue(new IntegerType(statusCode));
		response.addParameter().setName("filtered").setValue(new BooleanType(filtered));
		response.addParameter().setName("responseBodyJson").setValue(new StringType(filteredBodyJson));
		return response;
	}

	private FhirAuthorizationEngine authorizationEngine(String theFhirVersion, List<IBaseResource> theInlinePermissions) {
		PermissionService permissionService =
				theInlinePermissions.isEmpty() ? myPermissionService : context -> theInlinePermissions;
		return new FhirAuthorizationEngine(permissionService, permissionEvaluator(theFhirVersion));
	}

	private PermissionEvaluator permissionEvaluator(String theFhirVersion) {
		return "R5".equalsIgnoreCase(theFhirVersion) ? new SimpleR5PermissionEvaluator() : new SimpleR4PermissionEvaluator();
	}

	private FhirRequest buildFhirRequest(Parameters theParameters, String theFhirVersion) {
		String method = getRequiredString(theParameters, "method");
		String url = getFirstString(theParameters, "url", null);
		String resourceType = getFirstString(theParameters, "resourceType", null);
		String resourceId = getFirstString(theParameters, "resourceId", null);
		String operationName = getFirstString(theParameters, "operationName", null);
		String queryString = buildQueryString(theParameters, url);
		String bodyJson = getFirstString(theParameters, "bodyJson", null);

		if (url != null) {
			ResolvedRequestPath resolved = resolveFromUrl(url);
			if (isBlank(resourceType)) {
				resourceType = resolved.resourceType;
			}
			if (isBlank(resourceId)) {
				resourceId = resolved.resourceId;
			}
			if (isBlank(operationName)) {
				operationName = resolved.operationName;
			}
		}

		FhirRequest request = new FhirRequest()
				.setMethod(FhirRequest.HTTPVerb.valueOf(method.toUpperCase(Locale.ROOT)))
				.setResourceType(resourceType)
				.setResourceId(resourceId)
				.setOperationName(operationName)
				.setSearchParameters(parseQueryString(queryString));

		if (!isBlank(bodyJson)) {
			request.setBody(parsePayload(bodyJson, theFhirVersion));
		}
		return request;
	}

	private List<IBaseResource> parseInlinePermissions(Parameters theParameters) {
		List<String> permissionJsonValues = getRepeatedStrings(theParameters, "permissionJson");
		if (permissionJsonValues.isEmpty()) {
			return List.of();
		}
		if (!myPermissionProperties.isAllowInlinePermissions()) {
			throw new InvalidRequestException("Inline permissionJson parameters are disabled by configuration");
		}

		List<IBaseResource> resources = new ArrayList<>();
		var parser = ca.uhn.fhir.context.FhirContext.forR5().newJsonParser();
		for (String permissionJson : permissionJsonValues) {
			try {
				IBaseResource resource = parser.parseResource(permissionJson);
				if (!(resource instanceof Permission)) {
					throw new UnprocessableEntityException("permissionJson must represent an R5 Permission resource");
				}
				resources.add(resource);
			} catch (RuntimeException e) {
				ourLog.warn("Unable to parse inline permissionJson", e);
				throw new UnprocessableEntityException("Unable to parse permissionJson parameter");
			}
		}
		return resources;
	}

	private IBaseResource parsePayload(String theJsonPayload, String theFhirVersion) {
		try {
			return parserFor(theFhirVersion).parseResource(theJsonPayload);
		} catch (RuntimeException e) {
			ourLog.warn("Unable to parse {} payload", theFhirVersion, e);
			throw new UnprocessableEntityException("Unable to parse FHIR payload for version " + theFhirVersion);
		}
	}

	private String serializePayload(IBaseResource theResource, String theFhirVersion) {
		return parserFor(theFhirVersion).encodeResourceToString(theResource);
	}

	private ca.uhn.fhir.parser.IParser parserFor(String theFhirVersion) {
		var parser = "R5".equalsIgnoreCase(theFhirVersion)
				? ca.uhn.fhir.context.FhirContext.forR5().newJsonParser()
				: ca.uhn.fhir.context.FhirContext.forR4().newJsonParser();
		parser.setPrettyPrint(false);
		return parser;
	}

	private String buildQueryString(Parameters theParameters, String theUrl) {
		String explicitQueryString = getFirstString(theParameters, "queryString", null);
		if (!isBlank(explicitQueryString)) {
			return explicitQueryString;
		}

		List<String> repeatedQueryParameters = getRepeatedStrings(theParameters, "queryParameter");
		if (!repeatedQueryParameters.isEmpty()) {
			return String.join("&", repeatedQueryParameters);
		}

		if (theUrl != null && theUrl.contains("?")) {
			return theUrl.substring(theUrl.indexOf('?') + 1);
		}
		return "";
	}

	private Map<String, List<String>> parseQueryString(String theQueryString) {
		Map<String, List<String>> parameters = new LinkedHashMap<>();
		if (isBlank(theQueryString)) {
			return parameters;
		}

		for (String pair : theQueryString.split("&")) {
			if (pair == null || pair.isBlank()) {
				continue;
			}
			String[] keyValue = pair.split("=", 2);
			String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
			String rawValue = keyValue.length > 1 ? keyValue[1] : "";
			List<String> values = new ArrayList<>();
			for (String value : rawValue.split(",")) {
				values.add(URLDecoder.decode(value, StandardCharsets.UTF_8));
			}
			parameters.put(key, values);
		}
		return parameters;
	}

	private String toQueryString(Map<String, List<String>> theParameters) {
		if (theParameters == null || theParameters.isEmpty()) {
			return "";
		}
		return theParameters.entrySet().stream()
				.map(entry -> entry.getKey() + "=" + entry.getValue().stream()
						.map(value -> URLEncoder.encode(value, StandardCharsets.UTF_8))
						.collect(Collectors.joining(",")))
				.collect(Collectors.joining("&"));
	}

	private void addQueryParameters(Parameters theResponse, String theParameterName, Map<String, List<String>> theParameters) {
		if (theParameters == null) {
			return;
		}
		for (Map.Entry<String, List<String>> entry : theParameters.entrySet()) {
			theResponse.addParameter()
					.setName(theParameterName)
					.setValue(new StringType(entry.getKey() + "=" + String.join(",", entry.getValue())));
		}
	}

	private String getRequiredString(Parameters theParameters, String theName) {
		String value = getFirstString(theParameters, theName, null);
		if (isBlank(value)) {
			throw new InvalidRequestException("Missing required parameter: " + theName);
		}
		return value;
	}

	private String getFirstString(Parameters theParameters, String theName, String theDefaultValue) {
		return theParameters.getParameter().stream()
				.filter(parameter -> theName.equals(parameter.getName()))
				.map(Parameters.ParametersParameterComponent::getValue)
				.filter(Objects::nonNull)
				.map(value -> value.primitiveValue())
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(theDefaultValue);
	}

	private int getFirstInteger(Parameters theParameters, String theName, int theDefaultValue) {
		String rawValue = getFirstString(theParameters, theName, null);
		if (isBlank(rawValue)) {
			return theDefaultValue;
		}
		try {
			return Integer.parseInt(rawValue);
		} catch (NumberFormatException e) {
			throw new InvalidRequestException("Parameter " + theName + " must be an integer");
		}
	}

	private List<String> getRepeatedStrings(Parameters theParameters, String theName) {
		return theParameters.getParameter().stream()
				.filter(parameter -> theName.equals(parameter.getName()))
				.map(Parameters.ParametersParameterComponent::getValue)
				.filter(Objects::nonNull)
				.map(value -> value.primitiveValue())
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	private boolean isBlank(String theValue) {
		return theValue == null || theValue.isBlank();
	}

	private ResolvedRequestPath resolveFromUrl(String theUrl) {
		String cleaned = theUrl;
		int schemeSeparator = cleaned.indexOf("://");
		if (schemeSeparator >= 0) {
			int firstSlashAfterHost = cleaned.indexOf('/', schemeSeparator + 3);
			cleaned = firstSlashAfterHost >= 0 ? cleaned.substring(firstSlashAfterHost) : "/";
		}
		int queryIndex = cleaned.indexOf('?');
		if (queryIndex >= 0) {
			cleaned = cleaned.substring(0, queryIndex);
		}
		cleaned = cleaned.replaceAll("^/+", "");

		String[] segments = cleaned.split("/");
		List<String> nonEmptySegments = new ArrayList<>();
		for (String segment : segments) {
			if (!segment.isBlank()) {
				nonEmptySegments.add(segment);
			}
		}

		int fhirIndex = nonEmptySegments.indexOf("fhir");
		List<String> effectiveSegments = fhirIndex >= 0 && fhirIndex + 1 < nonEmptySegments.size()
				? nonEmptySegments.subList(fhirIndex + 1, nonEmptySegments.size())
				: nonEmptySegments;

		ResolvedRequestPath resolved = new ResolvedRequestPath();
		if (!effectiveSegments.isEmpty()) {
			resolved.resourceType = effectiveSegments.get(0);
		}
		if (effectiveSegments.size() > 1) {
			String secondSegment = effectiveSegments.get(1);
			if (secondSegment.startsWith("$")) {
				resolved.operationName = secondSegment.substring(1);
			} else {
				resolved.resourceId = secondSegment;
			}
		}
		if (effectiveSegments.size() > 2 && resolved.operationName == null) {
			String thirdSegment = effectiveSegments.get(2);
			if (thirdSegment.startsWith("$")) {
				resolved.operationName = thirdSegment.substring(1);
			}
		}
		return resolved;
	}

	private static class ResolvedRequestPath {
		private String resourceType;
		private String resourceId;
		private String operationName;
	}
}
