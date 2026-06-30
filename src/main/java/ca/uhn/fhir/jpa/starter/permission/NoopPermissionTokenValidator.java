package ca.uhn.fhir.jpa.starter.permission;

public class NoopPermissionTokenValidator implements PermissionTokenValidator {

	@Override
	public PermissionValidatedToken validate(String token) {
		return PermissionValidatedToken.anonymous();
	}
}
