package ca.uhn.fhir.jpa.starter.mapping.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hapi.fhir.mapping")
public class MappingProperties {

	private String terminologyEndpoint;
	private String structureMapEndpoint;

	public String getTerminologyEndpoint() {
		return terminologyEndpoint;
	}

	public void setTerminologyEndpoint(String terminologyEndpoint) {
		this.terminologyEndpoint = terminologyEndpoint;
	}

	public String getStructureMapEndpoint() {
		return structureMapEndpoint;
	}

	public void setStructureMapEndpoint(String structureMapEndpoint) {
		this.structureMapEndpoint = structureMapEndpoint;
	}
}
