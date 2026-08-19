package ca.uhn.fhir.jpa.starter.mapping.service;

import org.hl7.fhir.r4.model.StructureMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperRoutingTest {

	@Test
	void crossVersionOfficialHl7StructuresUseMatchbox() {
		StructureMap structureMap = createMap(
				"http://hl7.org/fhir/4.0/StructureDefinition/Appointment",
				"http://hl7.org/fhir/5.0/StructureDefinition/Appointment");

		assertTrue(Mapper.isCrossVersionHl7StructureMap(structureMap, structureMap.getGroupFirstRep()));
	}

	@Test
	void customStructuresUseNativeMapper() {
		StructureMap structureMap = createMap(
				"http://fyrstain.com/fhir/R4/stjo-ig/StructureDefinition/PR-Patient",
				"http://fyrstain.com/fhir/R4/stjo-ig/StructureDefinition/PR-Bundle");

		assertFalse(Mapper.isCrossVersionHl7StructureMap(structureMap, structureMap.getGroupFirstRep()));
		assertFalse(Mapper.usesNonR4Hl7StructureMap(structureMap, structureMap.getGroupFirstRep()));
	}

	@Test
	void officialR5ToR5StructuresDoNotUseTheR4NativeMapper() {
		StructureMap structureMap = createMap(
				"http://hl7.org/fhir/5.0/StructureDefinition/Appointment",
				"http://hl7.org/fhir/5.0/StructureDefinition/Appointment");

		assertFalse(Mapper.isCrossVersionHl7StructureMap(structureMap, structureMap.getGroupFirstRep()));
		assertTrue(Mapper.usesNonR4Hl7StructureMap(structureMap, structureMap.getGroupFirstRep()));
	}

	private StructureMap createMap(String sourceUrl, String targetUrl) {
		StructureMap structureMap = new StructureMap();
		structureMap.addStructure()
				.setUrl(sourceUrl)
				.setAlias("sourceModel")
				.setMode(StructureMap.StructureMapModelMode.SOURCE);
		structureMap.addStructure()
				.setUrl(targetUrl)
				.setAlias("targetModel")
				.setMode(StructureMap.StructureMapModelMode.TARGET);

		structureMap.addGroup()
				.addInput()
				.setName("source")
				.setType("sourceModel")
				.setMode(StructureMap.StructureMapInputMode.SOURCE);
		structureMap.getGroupFirstRep()
				.addInput()
				.setName("target")
				.setType("targetModel")
				.setMode(StructureMap.StructureMapInputMode.TARGET);

		return structureMap;
	}
}
