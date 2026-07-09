package ca.uhn.fhir.jpa.starter.permission.service;

import com.fyrstain.fhir.security.core.PermissionService;
import com.fyrstain.fhir.security.core.model.PermissionContext;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.ArrayList;
import java.util.List;

public class CompositePermissionService implements PermissionService {

	private final List<PermissionSource> myPermissionSources;

	public CompositePermissionService(List<PermissionSource> thePermissionSources) {
		myPermissionSources = thePermissionSources != null ? thePermissionSources : List.of();
	}

	@Override
	public List<IBaseResource> getPermissions(PermissionContext context) {
		List<IBaseResource> permissions = new ArrayList<>();
		for (PermissionSource permissionSource : myPermissionSources) {
			permissions.addAll(permissionSource.getPermissions(context));
		}
		return permissions;
	}
}
