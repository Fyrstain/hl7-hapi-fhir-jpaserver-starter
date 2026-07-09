package ca.uhn.fhir.jpa.starter.permission.service;

import com.fyrstain.fhir.security.core.PermissionService;
import com.fyrstain.fhir.security.core.model.PermissionContext;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;

public class NoopPermissionService implements PermissionService {

	@Override
	public List<IBaseResource> getPermissions(PermissionContext context) {
		return List.of();
	}
}
