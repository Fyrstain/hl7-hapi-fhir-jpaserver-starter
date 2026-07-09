package ca.uhn.fhir.jpa.starter.permission.service;

import com.fyrstain.fhir.security.core.model.PermissionContext;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;

public interface PermissionSource {

	List<IBaseResource> getPermissions(PermissionContext context);
}
