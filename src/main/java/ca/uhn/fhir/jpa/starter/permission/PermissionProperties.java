package ca.uhn.fhir.jpa.starter.permission;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "hapi.fhir.permission")
public class PermissionProperties {

	private boolean enabled = false;
	private boolean exposeOperations = true;
	private boolean enableInterceptors = false;
	private boolean allowInlinePermissions = true;
	private boolean requireToken = false;
	private boolean failClosed = true;
	private String defaultFhirVersion = "R4";
	private TokenValidation tokenValidation = new TokenValidation();
	private TokenSource tokenSource = new TokenSource();
	private Repository repository = new Repository();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isExposeOperations() {
		return exposeOperations;
	}

	public void setExposeOperations(boolean exposeOperations) {
		this.exposeOperations = exposeOperations;
	}

	public boolean isAllowInlinePermissions() {
		return allowInlinePermissions;
	}

	public void setAllowInlinePermissions(boolean allowInlinePermissions) {
		this.allowInlinePermissions = allowInlinePermissions;
	}

	public boolean isEnableInterceptors() {
		return enableInterceptors;
	}

	public void setEnableInterceptors(boolean enableInterceptors) {
		this.enableInterceptors = enableInterceptors;
	}

	public boolean isRequireToken() {
		return requireToken;
	}

	public void setRequireToken(boolean requireToken) {
		this.requireToken = requireToken;
	}

	public boolean isFailClosed() {
		return failClosed;
	}

	public void setFailClosed(boolean failClosed) {
		this.failClosed = failClosed;
	}

	public String getDefaultFhirVersion() {
		return defaultFhirVersion;
	}

	public void setDefaultFhirVersion(String defaultFhirVersion) {
		this.defaultFhirVersion = defaultFhirVersion;
	}

	public TokenValidation getTokenValidation() {
		return tokenValidation;
	}

	public void setTokenValidation(TokenValidation tokenValidation) {
		this.tokenValidation = tokenValidation;
	}

	public TokenSource getTokenSource() {
		return tokenSource;
	}

	public void setTokenSource(TokenSource tokenSource) {
		this.tokenSource = tokenSource;
	}

	public Repository getRepository() {
		return repository;
	}

	public void setRepository(Repository repository) {
		this.repository = repository;
	}

	public static class TokenValidation {

		private boolean enabled = false;
		private String mode = "none";
		private String sharedSecret;
		private String jwkSetUri;
		private String issuer;
		private String audience;
		private String principalClaim = "sub";
		private String displayNameClaim = "preferred_username";
		private String organizationClaim = "organization_id";
		private List<String> roleClaims = new ArrayList<>(List.of("realm_access.roles", "roles", "scope", "scp"));

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getMode() {
			return mode;
		}

		public void setMode(String mode) {
			this.mode = mode;
		}

		public String getSharedSecret() {
			return sharedSecret;
		}

		public void setSharedSecret(String sharedSecret) {
			this.sharedSecret = sharedSecret;
		}

		public String getJwkSetUri() {
			return jwkSetUri;
		}

		public void setJwkSetUri(String jwkSetUri) {
			this.jwkSetUri = jwkSetUri;
		}

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}

		public String getAudience() {
			return audience;
		}

		public void setAudience(String audience) {
			this.audience = audience;
		}

		public String getPrincipalClaim() {
			return principalClaim;
		}

		public void setPrincipalClaim(String principalClaim) {
			this.principalClaim = principalClaim;
		}

		public String getDisplayNameClaim() {
			return displayNameClaim;
		}

		public void setDisplayNameClaim(String displayNameClaim) {
			this.displayNameClaim = displayNameClaim;
		}

		public String getOrganizationClaim() {
			return organizationClaim;
		}

		public void setOrganizationClaim(String organizationClaim) {
			this.organizationClaim = organizationClaim;
		}

		public List<String> getRoleClaims() {
			return roleClaims;
		}

		public void setRoleClaims(List<String> roleClaims) {
			this.roleClaims = roleClaims;
		}
	}

	public static class TokenSource {

		private boolean enabled = true;
		private List<String> claimNames = new ArrayList<>(List.of("permissions", "permission", "permission_json"));

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public List<String> getClaimNames() {
			return claimNames;
		}

		public void setClaimNames(List<String> claimNames) {
			this.claimNames = claimNames;
		}
	}

	public static class Repository {

		private boolean enabled = false;
		private String baseUrl;
		private String queryTemplate;
		private boolean reuseIncomingToken = true;
		private String bearerToken;
		private int timeoutSeconds = 10;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getQueryTemplate() {
			return queryTemplate;
		}

		public void setQueryTemplate(String queryTemplate) {
			this.queryTemplate = queryTemplate;
		}

		public boolean isReuseIncomingToken() {
			return reuseIncomingToken;
		}

		public void setReuseIncomingToken(boolean reuseIncomingToken) {
			this.reuseIncomingToken = reuseIncomingToken;
		}

		public String getBearerToken() {
			return bearerToken;
		}

		public void setBearerToken(String bearerToken) {
			this.bearerToken = bearerToken;
		}

		public int getTimeoutSeconds() {
			return timeoutSeconds;
		}

		public void setTimeoutSeconds(int timeoutSeconds) {
			this.timeoutSeconds = timeoutSeconds;
		}
	}
}
