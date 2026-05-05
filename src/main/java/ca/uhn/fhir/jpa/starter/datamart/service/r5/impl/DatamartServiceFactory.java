package ca.uhn.fhir.jpa.starter.datamart.service.r5.impl;

import ca.uhn.fhir.rest.api.server.RequestDetails;

@FunctionalInterface
public interface DatamartServiceFactory {
	DatamartServiceImpl create(RequestDetails requestDetails);
}

