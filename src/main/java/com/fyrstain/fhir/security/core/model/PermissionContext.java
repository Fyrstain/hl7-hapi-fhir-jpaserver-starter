package com.fyrstain.fhir.security.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PermissionContext {

    private final String userId;
    private final String displayName;
    private final String token;
    private final Set<String> roles;
    private final String organizationId;
    private final Map<String, Object> attributes;

    public PermissionContext(
            String userId,
            String displayName,
            String token,
            Set<String> roles,
            String organizationId
    ) {
        this(userId, displayName, token, roles, organizationId, Collections.emptyMap());
    }

    public PermissionContext(
            String userId,
            String displayName,
            String token,
            Set<String> roles,
            String organizationId,
            Map<String, Object> attributes
    ) {
        this.userId = userId;
        this.displayName = displayName;
        this.token = token;
        this.roles = roles != null ? roles : Collections.emptySet();
        this.organizationId = organizationId;
        this.attributes = attributes != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(attributes))
                : Collections.emptyMap();
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getToken() {
        return token;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }
}
