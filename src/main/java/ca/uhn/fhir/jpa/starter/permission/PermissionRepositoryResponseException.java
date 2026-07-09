package ca.uhn.fhir.jpa.starter.permission;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;

public class PermissionRepositoryResponseException extends BaseServerResponseException {

	public PermissionRepositoryResponseException(String theMessage) {
		super(502, theMessage);
	}

	public PermissionRepositoryResponseException(String theMessage, Throwable theCause) {
		super(502, theMessage, theCause);
	}
}
