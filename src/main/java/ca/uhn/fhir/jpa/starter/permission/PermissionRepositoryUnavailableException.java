package ca.uhn.fhir.jpa.starter.permission;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;

public class PermissionRepositoryUnavailableException extends BaseServerResponseException {

	public PermissionRepositoryUnavailableException(String theMessage) {
		super(503, theMessage);
	}

	public PermissionRepositoryUnavailableException(String theMessage, Throwable theCause) {
		super(503, theMessage, theCause);
	}
}
