package ca.uhn.fhir.jpa.starter.datamart.service.r5.impl;

import org.hl7.fhir.r5.model.Binary;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.Endpoint;

public interface DatamartExportServiceImpl {
	Binary exportDatamart(CanonicalType researchStudyUrl, Endpoint researchStudyEndpoint, Endpoint structureMapEndpoint, Endpoint terminologyEndpoint, Endpoint remoteEndpoint, String type, CanonicalType structureMapUrl);
}
