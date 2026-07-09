package ca.uhn.fhir.jpa.starter.permission.service;

import ca.uhn.fhir.jpa.starter.permission.PermissionClaimUtils;
import ca.uhn.fhir.jpa.starter.permission.PermissionContextAttributes;
import ca.uhn.fhir.jpa.starter.permission.PermissionProperties;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyrstain.fhir.security.core.model.PermissionContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.Permission;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class TokenClaimPermissionSource implements PermissionSource {

	private final PermissionProperties myPermissionProperties;
	private final ObjectMapper myObjectMapper;
	private final ca.uhn.fhir.parser.IParser myParser = ca.uhn.fhir.context.FhirContext.forR5().newJsonParser();

	public TokenClaimPermissionSource(PermissionProperties thePermissionProperties, ObjectMapper theObjectMapper) {
		myPermissionProperties = thePermissionProperties;
		myObjectMapper = theObjectMapper;
		myParser.setPrettyPrint(false);
	}

	@Override
	public List<IBaseResource> getPermissions(PermissionContext context) {
		Object claimsObject = context.getAttribute(PermissionContextAttributes.CLAIMS);
		if (!(claimsObject instanceof Map<?, ?> claimsMap)) {
			return List.of();
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> claims = (Map<String, Object>) claimsMap;
		List<IBaseResource> permissions = new ArrayList<>();
		for (Object value : PermissionClaimUtils.getRawValues(claims, myPermissionProperties.getTokenSource().getClaimNames())) {
			collectPermissions(value, permissions);
		}
		return permissions;
	}

	private void collectPermissions(Object value, List<IBaseResource> permissions) {
		if (value == null) {
			return;
		}
		if (value instanceof String stringValue) {
			parseStringValue(stringValue, permissions);
			return;
		}
		if (value instanceof Collection<?> collection) {
			for (Object item : collection) {
				collectPermissions(item, permissions);
			}
			return;
		}
		if (value instanceof Map<?, ?>) {
			parseResourceJson(serialize(value), permissions);
			return;
		}
		throw new AuthenticationException("Unsupported permission claim format in token");
	}

	private void parseStringValue(String stringValue, List<IBaseResource> permissions) {
		if (!StringUtils.hasText(stringValue)) {
			return;
		}
		String trimmed = stringValue.trim();
		if (trimmed.startsWith("[")) {
			try {
				JsonNode node = myObjectMapper.readTree(trimmed);
				if (node.isArray()) {
					for (JsonNode element : node) {
						if (element.isTextual()) {
							parseResourceJson(element.asText(), permissions);
						} else {
							parseResourceJson(myObjectMapper.writeValueAsString(element), permissions);
						}
					}
					return;
				}
			} catch (JsonProcessingException e) {
				throw new AuthenticationException("Unable to parse permission claim array");
			}
		}
		parseResourceJson(trimmed, permissions);
	}

	private void parseResourceJson(String resourceJson, List<IBaseResource> permissions) {
		try {
			IBaseResource resource = myParser.parseResource(resourceJson);
			if (!(resource instanceof Permission)) {
				throw new AuthenticationException("Permission claim must contain R5 Permission resources");
			}
			permissions.add(resource);
		} catch (RuntimeException e) {
			throw new AuthenticationException("Unable to parse permission claim resource");
		}
	}

	private String serialize(Object value) {
		try {
			return myObjectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new AuthenticationException("Unable to serialize permission claim");
		}
	}
}
