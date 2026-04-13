package ca.uhn.fhir.jpa.starter.mapping.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.apache.jena.ext.xerces.impl.dv.util.Base64;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.context.IWorkerContext;
import org.hl7.fhir.r4.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r4.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StructureMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MapperFhirToFhirWithMatchboxTest {

    private ValidationSupportChain validationSupport;
    private IWorkerContext hapiContext;
    private FHIRPathEngine fhirPathEngine;

    private IFhirResourceDao<StructureMap> structureMapDao;
    private IGenericClient clientStructureMap;
    private MatchboxTransformService matchboxTransformService;

    private Mapper mapper;

    @BeforeEach
    void setUp() {
        FhirContext context = FhirContext.forR4();

        PrePopulatedValidationSupport prePopulatedValidationSupport =
            new PrePopulatedValidationSupport(context);

        this.validationSupport = new ValidationSupportChain(
            prePopulatedValidationSupport,
            new DefaultProfileValidationSupport(context)
        );

        this.hapiContext = new HapiWorkerContext(context, this.validationSupport);
        this.fhirPathEngine = new FHIRPathEngine(hapiContext);

        this.structureMapDao = null;
        this.clientStructureMap = null;
        this.matchboxTransformService = mock(MatchboxTransformService.class);

        this.mapper = new Mapper(
            hapiContext,
            fhirPathEngine,
            null,
            structureMapDao,
            clientStructureMap,
            matchboxTransformService
        );
    }

    @Test
    void mapFhirToFhir_usesMatchboxAndReturnsBinaryOutput() {
        // given
        StructureMap structureMap = getStructureMap();

        String inputJson = """
            {
              "resourceType": "Patient",
              "id": "pat-r4-001"
            }
            """;

        String outputJson = """
            {
              "resourceType": "Patient",
              "id": "pat-r5-001"
            }
            """;

        when(matchboxTransformService.transform(
            any(StructureMap.class),
            anyList(),
            isNull(),
            eq(inputJson),
			  	eq(true)
        )).thenReturn(outputJson);

        Parameters parameters = buildInputParameters("source", "application/fhir+json", inputJson);

        // when
        Parameters result = mapper.map(structureMap, parameters);

        // then
        assertNotNull(result);

        Parameters.ParametersParameterComponent targetParam = result.getParameter("target");
        assertNotNull(targetParam);
        assertNotNull(targetParam.getResource());
        assertInstanceOf(Binary.class, targetParam.getResource());

        Binary outputBinary = (Binary) targetParam.getResource();
        assertEquals("application/fhir+json", outputBinary.getContentType());

        String decodedOutput = new String(
            java.util.Base64.getDecoder().decode(outputBinary.getContentAsBase64()),
            StandardCharsets.UTF_8
        );

        assertEquals(outputJson, decodedOutput);

        verify(matchboxTransformService, times(1)).transform(
            any(StructureMap.class),
            anyList(),
            isNull(),
            eq(inputJson),
			   eq(true)
        );
    }

    @Test
    void mapFhirToFhir_propagatesMatchboxException() {
        // given
        StructureMap structureMap = getStructureMap();

        String inputJson = """
            {
              "resourceType": "Patient",
              "id": "pat-r4-001"
            }
            """;

        when(matchboxTransformService.transform(
            any(StructureMap.class),
            anyList(),
            isNull(),
            eq(inputJson),
			  eq(true)
        )).thenThrow(new RuntimeException("boom"));

        Parameters parameters = buildInputParameters("source", "application/fhir+json", inputJson);

        // when / then
        RuntimeException ex = assertThrows(
            RuntimeException.class,
            () -> mapper.map(structureMap, parameters)
        );

        assertEquals("boom", ex.getMessage());
    }

    private Parameters buildInputParameters(String partName, String contentType, String content) {
        Parameters.ParametersParameterComponent param = new Parameters.ParametersParameterComponent();
        param.setName("input");

        param.addPart(
            new Parameters.ParametersParameterComponent()
                .setName(partName)
                .setResource(
                    new Binary()
                        .setContentType(contentType)
                        .setContentAsBase64(Base64.encode(content.getBytes(StandardCharsets.UTF_8)))
                )
        );

        return new Parameters().addParameter(param);
    }

    private StructureMap getStructureMap() {
        StructureMap structureMap = new StructureMap();
        structureMap.setUrl("http://example.org/StructureMap/patient-r4-to-r5");

        StructureMap.StructureMapGroupComponent group = new StructureMap.StructureMapGroupComponent();
        group.setName("main");
        group.setTypeMode(StructureMap.StructureMapGroupTypeMode.TYPES);

        StructureMap.StructureMapGroupInputComponent inputSource =
            new StructureMap.StructureMapGroupInputComponent();
        inputSource.setName("source");
        inputSource.setType("Patient");
        inputSource.setMode(StructureMap.StructureMapInputMode.SOURCE);

        StructureMap.StructureMapGroupInputComponent inputTarget =
            new StructureMap.StructureMapGroupInputComponent();
        inputTarget.setName("target");
        inputTarget.setType("Patient");
        inputTarget.setMode(StructureMap.StructureMapInputMode.TARGET);

        group.addInput(inputSource);
        group.addInput(inputTarget);

        structureMap.addGroup(group);
        return structureMap;
    }
}