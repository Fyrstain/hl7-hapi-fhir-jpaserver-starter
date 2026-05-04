package ca.uhn.fhir.jpa.starter.mapping.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.mapping.model.exception.MatchboxTransformException;
import ch.ahdis.matchbox.engine.CdaMappingEngine;
import ch.ahdis.matchbox.engine.MatchboxEngine;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.StructureMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class MatchboxTransformServiceTest {

    private FhirContext fhirContext;
    private MatchboxTransformService service;

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
		 fhirContext = FhirContext.forR4Cached();
		 service = new MatchboxTransformService(fhirContext, new CdaMappingEngine.CdaMappingEngineBuilder().getCdaEngineR4());
    }

    @Test
    void transform_ok_returnsResource() throws IOException {
        StructureMap structureMap = loadStructureMap("/matchbox/structuremap-example.json");
        String sourceJson = loadText("/matchbox/source-example.json");

        Resource result = new org.hl7.fhir.r4.formats.JsonParser()
			  .parse(service.transform(structureMap, sourceJson));

        assertNotNull(result);
        assertNotNull(result.fhirType());
		  assertEquals("Patient", result.fhirType());
		  assertEquals("testID", result.getId());
    }

	@Test
	void transform_ko_returnsResource_withImport_missingSM() {
		StructureMap structureMap = loadStructureMap("/matchbox/simple-patient-main.json");
		String sourceJson = loadText("/matchbox/source-example.json");

		Exception e = assertThrows(MatchboxTransformException.class, () -> service.transform(structureMap, sourceJson));

		assertNotNull(e.getCause());
		assertNotNull(e.getCause().getMessage());
		assertEquals("Unable to find map(s) for http://example.org/fhir/StructureMap/simple-patient-common", e.getCause().getMessage());
	}

	@Test
	void transform_ok_returnsResource_withImport() throws IOException {
		StructureMap structureMap = loadStructureMap("/matchbox/simple-patient-main.json");
		StructureMap structureMapCommon = loadStructureMap("/matchbox/simple-patient-common.json");
		String sourceJson = loadText("/matchbox/source-example.json");

		Resource result = new org.hl7.fhir.r4.formats.JsonParser()
			.parse(service.transform(structureMap, List.of(structureMapCommon), null, sourceJson, true));

		assertNotNull(result);
		assertNotNull(result.fhirType());
		assertEquals("Patient", result.fhirType());
		assertEquals("testID", result.getId());
	}

    @Test
    void transform_withoutCanonicalUrl_throwsIllegalArgumentException() {
        StructureMap structureMap = loadStructureMap("/matchbox/structuremap-example.json");
        structureMap.setUrl((String) null);

        String sourceJson = loadText("/matchbox/source-example.json");

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.transform(structureMap, sourceJson)
        );

        assertTrue(ex.getMessage().contains("StructureMap.url"));
    }

    @Test
    void transform_withInvalidJson_wrapsException() {
        StructureMap structureMap = loadStructureMap("/matchbox/structuremap-example.json");
        String invalidJson = "{ not-valid-json }";

        MatchboxTransformException ex = assertThrows(
            MatchboxTransformException.class,
            () -> service.transform(structureMap, invalidJson)
        );

        assertNotNull(ex.getCause());
    }

    @Test
    void transform_withNullStructureMap_throwsNullPointerException() {
        String sourceJson = loadText("/matchbox/source-example.json");

        NullPointerException ex = assertThrows(
            NullPointerException.class,
            () -> service.transform(null, sourceJson)
        );

        assertTrue(ex.getMessage().contains("structureMap"));
    }

    @Test
    void transform_withNullSourceJson_throwsNullPointerException() {
        StructureMap structureMap = loadStructureMap("/matchbox/structuremap-example.json");

        NullPointerException ex = assertThrows(
            NullPointerException.class,
            () -> service.transform(structureMap, null)
        );

        assertTrue(ex.getMessage().contains("sourceString"));
    }

	@Test
	void transform_withCustomSourceModel_ok_returnsPatient() throws IOException {
		StructureDefinition customSourceModel =
			loadStructureDefinition("/matchbox/custom/my-patient-input-sd.json");

		StructureMap structureMap =
			loadStructureMap("/matchbox/custom/my-patient-input-to-patient.json");

		String sourceJson =
			loadText("/matchbox/custom/my-patient-input.json");

		Resource result = new org.hl7.fhir.r4.formats.XmlParser().parse(service.transform(
			structureMap,
			null,
			List.of(customSourceModel),
			sourceJson,
			false
		));

		assertNotNull(result);
		assertInstanceOf(Patient.class, result);

		Patient patient = (Patient) result;
		assertEquals("custom-patient-1", patient.getIdElement().getIdPart());
	}

	@Test
	void transform_fhirToCustom_ok_returnsNonNull() {
		StructureDefinition customTargetModel =
			loadStructureDefinition("/matchbox/custom/my-patient-output-sd.json");

		StructureMap structureMap =
			loadStructureMap("/matchbox/custom/patient-to-my-patient-output.json");

		String sourceJson =
			loadText("/matchbox/source-example.json");

		String result = service.transform(
			structureMap,
			null,
			List.of(customTargetModel),
			sourceJson,
			false
		);

		assertNotNull(result);
		assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"\n" +
			"<MyPatientOutput xmlns=\"http://hl7.org/fhir\">\n" +
			"  <id value=\"testID\"/>\n" +
			"</MyPatientOutput>", result);
	}

	@Test
	void transform_customToCustom_ok_returnsNonNull() {
		StructureDefinition customSourceModel =
			loadStructureDefinition("/matchbox/custom/my-patient-input-sd.json");

		StructureDefinition customTargetModel =
			loadStructureDefinition("/matchbox/custom/my-patient-output-sd.json");

		StructureMap structureMap =
			loadStructureMap("/matchbox/custom/my-patient-input-to-my-patient-output.json");

		String sourceJson =
			loadText("/matchbox/custom/my-patient-input.json");

		Object result = service.transform(
			structureMap,
			null,
			List.of(customSourceModel, customTargetModel),
			sourceJson,
			false
		);

		assertNotNull(result);
		assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"\n" +
			"<MyPatientOutput xmlns=\"http://hl7.org/fhir\">\n" +
			"  <id value=\"custom-patient-1\"/>\n" +
			"  <family value=\"Dupont\"/>\n" +
			"</MyPatientOutput>", result);
	}

	@Test
	void transform_cdaToBundle_simple() {

		StructureMap structureMap =
			loadStructureMap("/matchbox/cda/cda-to-bundle-simple.json");

		String cdaXml =
			loadText("/matchbox/cda/cda-simple.xml");

		String result = service.transform(structureMap, cdaXml);

		assertNotNull(result);
		assertFalse(result.isBlank());

		System.out.println(result);

		// assertions simples
		assertTrue(result.contains("Bundle"));
		assertTrue(result.contains("cda-12345"));
		assertTrue(result.contains("Test CDA Document"));
	}

	private StructureDefinition loadStructureDefinition(String path) {
		String json = loadText(path);
		Resource resource = (Resource) fhirContext.newJsonParser().parseResource(json);
		assertInstanceOf(StructureDefinition.class, resource);
		return (StructureDefinition) resource;
	}

    private StructureMap loadStructureMap(String path) {
        String json = loadText(path);
        Resource resource = (Resource) fhirContext.newJsonParser().parseResource(json);

        assertInstanceOf(StructureMap.class, resource, "Le fixture doit être un StructureMap R4");
        return (StructureMap) resource;
    }

    private String loadText(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            assertNotNull(is, "Ressource de test introuvable: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Impossible de lire la ressource de test " + path, e);
        }
    }
}