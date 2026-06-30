package ca.uhn.fhir.jpa.starter.permission;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PermissionValidatedToken(String token, Map<String, Object> claims) {

	public PermissionValidatedToken {
		claims = claims != null ? Collections.unmodifiableMap(new LinkedHashMap<>(claims)) : Collections.emptyMap();
	}

	public static PermissionValidatedToken anonymous() {
		return new PermissionValidatedToken(null, Collections.emptyMap());
	}
}
