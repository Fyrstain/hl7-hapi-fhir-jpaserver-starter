package ca.uhn.fhir.jpa.starter.permission;

public interface PermissionTokenValidator {

	PermissionValidatedToken validate(String token);
}
