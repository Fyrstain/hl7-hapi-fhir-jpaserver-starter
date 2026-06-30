package ca.uhn.fhir.jpa.starter.permission;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.fyrstain.fhir.security.core.model.PermissionContext;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PermissionContextFactory {

	private final PermissionProperties myPermissionProperties;
	private final PermissionTokenValidator myPermissionTokenValidator;

	public PermissionContextFactory(
			PermissionProperties thePermissionProperties, PermissionTokenValidator thePermissionTokenValidator) {
		myPermissionProperties = thePermissionProperties;
		myPermissionTokenValidator = thePermissionTokenValidator;
	}

	public PermissionContext fromParameters(Parameters theParameters) {
		String token = JwtPermissionTokenValidator.normalizeToken(
				firstString(theParameters, List.of("token", "authorization"), null));
		PermissionValidatedToken validatedToken = validateToken(token);
		Map<String, Object> claims = validatedToken.claims();
		Set<String> roles = new LinkedHashSet<>(repeatedStrings(theParameters, "role"));
		roles.addAll(PermissionClaimUtils.getStringSet(claims, myPermissionProperties.getTokenValidation().getRoleClaims()));

		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put(PermissionContextAttributes.CLAIMS, claims);
		attributes.put(PermissionContextAttributes.VALIDATED_TOKEN, validatedToken);
		String serverBase = firstString(theParameters, List.of("serverBase"), null);
		if (StringUtils.hasText(serverBase)) {
			attributes.put(PermissionContextAttributes.SERVER_BASE_URL, serverBase);
		}

		return new PermissionContext(
				resolveUserId(firstString(theParameters, List.of("userId"), null), claims),
				resolveDisplayName(firstString(theParameters, List.of("displayName"), null), claims),
				token,
				roles,
				resolveOrganizationId(firstString(theParameters, List.of("organizationId"), null), claims),
				attributes);
	}

	public PermissionContext fromRequestDetails(RequestDetails theRequestDetails, ServletRequestDetails theServletRequestDetails) {
		String token = JwtPermissionTokenValidator.normalizeToken(theRequestDetails.getHeader("Authorization"));
		PermissionValidatedToken validatedToken = validateToken(token);
		Map<String, Object> claims = validatedToken.claims();
		Set<String> roles = new LinkedHashSet<>(PermissionClaimUtils.getStringSet(
				claims, myPermissionProperties.getTokenValidation().getRoleClaims()));

		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put(PermissionContextAttributes.CLAIMS, claims);
		attributes.put(PermissionContextAttributes.VALIDATED_TOKEN, validatedToken);
		String serverBase = theRequestDetails.getFhirServerBase();
		if (!StringUtils.hasText(serverBase) && theServletRequestDetails != null) {
			serverBase = theServletRequestDetails.getServerBaseForRequest();
		}
		if (StringUtils.hasText(serverBase)) {
			attributes.put(PermissionContextAttributes.SERVER_BASE_URL, serverBase);
		}

		return new PermissionContext(
				resolveUserId(theRequestDetails.getHeader("X-User-Id"), claims),
				resolveDisplayName(theRequestDetails.getHeader("X-User-Name"), claims),
				token,
				roles,
				resolveOrganizationId(theRequestDetails.getHeader("X-Organization-Id"), claims),
				attributes);
	}

	private PermissionValidatedToken validateToken(String token) {
		if (myPermissionProperties.isRequireToken() && !StringUtils.hasText(token)) {
			throw new AuthenticationException("Missing authentication token");
		}
		if (!StringUtils.hasText(token)) {
			return PermissionValidatedToken.anonymous();
		}
		return myPermissionTokenValidator.validate(token);
	}

	private String resolveUserId(String explicitUserId, Map<String, Object> claims) {
		if (StringUtils.hasText(explicitUserId)) {
			return explicitUserId;
		}
		String fromConfiguredClaim = PermissionClaimUtils.getFirstString(
				claims, List.of(myPermissionProperties.getTokenValidation().getPrincipalClaim()));
		return StringUtils.hasText(fromConfiguredClaim)
				? fromConfiguredClaim
				: PermissionClaimUtils.getFirstString(claims, List.of("sub"));
	}

	private String resolveDisplayName(String explicitDisplayName, Map<String, Object> claims) {
		if (StringUtils.hasText(explicitDisplayName)) {
			return explicitDisplayName;
		}
		return firstNonBlank(
				PermissionClaimUtils.getFirstString(
						claims, List.of(myPermissionProperties.getTokenValidation().getDisplayNameClaim())),
				PermissionClaimUtils.getFirstString(claims, List.of("name", "preferred_username")));
	}

	private String resolveOrganizationId(String explicitOrganizationId, Map<String, Object> claims) {
		if (StringUtils.hasText(explicitOrganizationId)) {
			return explicitOrganizationId;
		}
		return PermissionClaimUtils.getFirstString(
				claims, List.of(myPermissionProperties.getTokenValidation().getOrganizationClaim()));
	}

	private String firstString(Parameters parameters, List<String> names, String defaultValue) {
		return parameters.getParameter().stream()
				.filter(parameter -> names.contains(parameter.getName()))
				.map(Parameters.ParametersParameterComponent::getValue)
				.filter(Objects::nonNull)
				.map(value -> value.primitiveValue())
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(defaultValue);
	}

	private List<String> repeatedStrings(Parameters parameters, String name) {
		return parameters.getParameter().stream()
				.filter(parameter -> name.equals(parameter.getName()))
				.map(Parameters.ParametersParameterComponent::getValue)
				.filter(Objects::nonNull)
				.map(value -> value.primitiveValue())
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	private String firstNonBlank(String first, String second) {
		return StringUtils.hasText(first) ? first : second;
	}
}
