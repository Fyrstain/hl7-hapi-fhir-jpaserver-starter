package ca.uhn.fhir.jpa.starter.datamart.r5;

import ca.uhn.fhir.jpa.starter.common.RemoteCqlClient;
import ca.uhn.fhir.jpa.starter.datamart.service.r5.service.DatamartGeneration;
import ca.uhn.fhir.jpa.starter.datamart.service.r5.utils.EvidenceVariableUtils;
import ca.uhn.fhir.jpa.starter.datamart.service.r5.utils.LibraryUtils;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r5.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opencds.cqf.fhir.api.Repository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DatamartGenerationTest {

	private static final String EXT_CQF_LIBRARY = "http://hl7.org/fhir/StructureDefinition/cqf-library";

	@Mock
	private RemoteCqlClient cql;

	@Mock
	private Repository repository;

	private DatamartGeneration generation;

	private static Parameters paramsFromBooleans(Map<String, Boolean> values) {
		Parameters p = new Parameters();
		values.forEach((k, v) -> p.addParameter().setName(k).setValue(new BooleanType(v)));
		return p;
	}

	private static boolean hasNamedParam(Parameters p, String name) {
		return p.getParameter().stream().anyMatch(pp -> name.equals(pp.getName()));
	}

	private static String idPart(String typeSlashId) {
		int idx = typeSlashId.indexOf('/');
		return idx >= 0 ? typeSlashId.substring(idx + 1) : typeSlashId;
	}

	@BeforeEach
	void setUp() {
		generation = new DatamartGeneration(cql, repository);
	}

	@Test
	void singleExpressionOneParametersPerSubject() {
		EvidenceVariable ev = new EvidenceVariable().setUrl("http://example.org/ev/EV1");
		addLibraryExtension(ev, "http://example.org/Library/L1");
		ev.addCharacteristic(expressionDef("A"));

		try (MockedStatic<LibraryUtils> lib = mockStatic(LibraryUtils.class)) {
			lib.when(() -> LibraryUtils.resolveLibraryId(any(Repository.class), eq(ev), anyString())).thenReturn("L1");

			stubPatientRead("123");

			when(cql.evaluateLibrary(any(Parameters.class), eq("L1"))).thenReturn(outBoolean("A", true));

			when(repository.create(any(Parameters.class))).thenReturn(methodOutcome("p-1"));

			ListResource list = generation.generateDatamart(study(), ev, List.of("Patient/123"), new Parameters(), "fallback");

			assertNotNull(list);
			assertEquals(1, list.getEntry().size(), "One Parameters should be created for the single subject");
			assertEquals("Parameters/p-1", list.getEntryFirstRep().getItem().getReference());

			Parameters created = captureCreatedParameters();
			assertTrue(created.getParameter().stream().anyMatch(pp -> "A".equals(pp.getName())));
			assertTrue(created.getParameter().stream().anyMatch(pp -> "Patient".equals(pp.getName())));
		}
	}

	@Test
	void combinationWithMultipleExpressionsAllAreEvaluated() {
		EvidenceVariable ev = new EvidenceVariable().setUrl("http://example.org/ev/EV2");
		addLibraryExtension(ev, "http://example.org/Library/L1");

		var c = new EvidenceVariable.EvidenceVariableCharacteristicComponent();
		var comb = new EvidenceVariable.EvidenceVariableCharacteristicDefinitionByCombinationComponent();
		comb.setCode(EvidenceVariable.CharacteristicCombination.ANYOF);
		comb.addCharacteristic(expressionDef("A"));
		comb.addCharacteristic(expressionDef("B"));
		c.setDefinitionByCombination(comb);
		ev.addCharacteristic(c);

		try (MockedStatic<LibraryUtils> lib = mockStatic(LibraryUtils.class)) {
			lib.when(() -> LibraryUtils.resolveLibraryId(any(Repository.class), eq(ev), anyString())).thenReturn("L1");

			stubPatientRead("10");

			when(cql.evaluateLibrary(any(Parameters.class), eq("L1")))
				.thenReturn(paramsFromBooleans(Map.of("A", true, "B", false)));

			when(repository.create(any(Parameters.class))).thenReturn(methodOutcome("p-10"));

			ListResource list = generation.generateDatamart(study(), ev, List.of("Patient/10"), new Parameters(), "fallback");

			assertNotNull(list);
			assertEquals(1, list.getEntry().size());
			assertEquals("Parameters/p-10", list.getEntryFirstRep().getItem().getReference());

			Parameters created = captureCreatedParameters();
			assertTrue(hasNamedParam(created, "A"));
			assertTrue(hasNamedParam(created, "B"));
		}
	}

	@Test
	void combinationWithMixedExpressionsAndNestedEV() {
		EvidenceVariable evRoot = new EvidenceVariable().setUrl("http://example.org/ev/ROOT");
		addLibraryExtension(evRoot, "http://example.org/Library/L1");

		EvidenceVariable.EvidenceVariableCharacteristicComponent rootC = new EvidenceVariable.EvidenceVariableCharacteristicComponent();
		var rootComb = new EvidenceVariable.EvidenceVariableCharacteristicDefinitionByCombinationComponent();
		rootComb.setCode(EvidenceVariable.CharacteristicCombination.ANYOF);

		rootComb.addCharacteristic(expressionDef("X"));

		EvidenceVariable.EvidenceVariableCharacteristicComponent nestedRef = new EvidenceVariable.EvidenceVariableCharacteristicComponent();
		nestedRef.setDefinitionCanonical("http://example.org/ev/NESTED");
		rootComb.addCharacteristic(nestedRef);
		rootC.setDefinitionByCombination(rootComb);
		evRoot.addCharacteristic(rootC);

		EvidenceVariable nestedEv = new EvidenceVariable().setUrl("http://example.org/ev/NESTED");
		addLibraryExtension(nestedEv, "http://example.org/Library/L2");
		nestedEv.addCharacteristic(expressionDef("Y"));

		try (MockedStatic<LibraryUtils> lib = mockStatic(LibraryUtils.class);
			  MockedStatic<EvidenceVariableUtils> evu = mockStatic(EvidenceVariableUtils.class)) {

			lib.when(() -> LibraryUtils.resolveLibraryId(any(Repository.class), eq(evRoot), anyString())).thenReturn("L1");
			lib.when(() -> LibraryUtils.resolveLibraryId(any(Repository.class), eq(nestedEv), anyString())).thenReturn("L2");

			evu.when(() -> EvidenceVariableUtils.resolveEvidenceVariable(any(Repository.class), eq("http://example.org/ev/NESTED")))
				.thenReturn(nestedEv);

			stubPatientRead("77");

			when(cql.evaluateLibrary(any(Parameters.class), eq("L1"))).thenReturn(outBoolean("X", true));
			when(cql.evaluateLibrary(any(Parameters.class), eq("L2"))).thenReturn(outBoolean("Y", true));

			when(repository.create(any(Parameters.class))).thenReturn(methodOutcome("p-77"));

			ListResource list = generation.generateDatamart(study(), evRoot, List.of("Patient/77"), new Parameters(), "fallback");

			assertNotNull(list);
			assertEquals(1, list.getEntry().size());
			assertEquals("Parameters/p-77", list.getEntryFirstRep().getItem().getReference());

			Parameters created = captureCreatedParameters();
			assertTrue(hasNamedParam(created, "X"), "Result should include output from L1");
			assertTrue(hasNamedParam(created, "Y"), "Result should include output from L2");
		}
	}

	@Test
	void generatesOneParametersPerSubject() {
		EvidenceVariable ev = new EvidenceVariable().setUrl("http://example.org/ev/MULTI");
		addLibraryExtension(ev, "http://example.org/Library/L1");
		ev.addCharacteristic(expressionDef("A"));

		try (MockedStatic<LibraryUtils> lib = mockStatic(LibraryUtils.class)) {
			lib.when(() -> LibraryUtils.resolveLibraryId(any(Repository.class), eq(ev), anyString())).thenReturn("L1");

			List<String> subjects = List.of("Patient/1", "Patient/2", "Patient/3");
			subjects.forEach(s -> stubPatientRead(idPart(s)));

			when(cql.evaluateLibrary(any(Parameters.class), eq("L1")))
				.thenReturn(outBoolean("A", true));

			when(repository.create(any(Parameters.class)))
				.thenReturn(methodOutcome("p-1"))
				.thenReturn(methodOutcome("p-2"))
				.thenReturn(methodOutcome("p-3"));

			ListResource list = generation.generateDatamart(study(), ev, subjects, new Parameters(), "fallback");

			assertNotNull(list);
			assertEquals(3, list.getEntry().size(), "One entry per subject is expected");
			assertEquals("Parameters/p-1", list.getEntry().get(0).getItem().getReference());
			assertEquals("Parameters/p-2", list.getEntry().get(1).getItem().getReference());
			assertEquals("Parameters/p-3", list.getEntry().get(2).getItem().getReference());

			assertEquals(ListResource.ListStatus.CURRENT, list.getStatus());
			assertEquals(Enumerations.ListMode.SNAPSHOT, list.getMode());
			assertTrue(list.getTitle() != null && list.getTitle().contains("http://example.org/study/RS1"));
		}
	}

	@Test
	void invalidSubjectFormatThrowsIllegalArgumentException() {
		EvidenceVariable ev = new EvidenceVariable().setUrl("http://example.org/ev/ERR1");
		ev.addCharacteristic(expressionDef("A"));

		try (MockedStatic<LibraryUtils> lib = mockStatic(LibraryUtils.class)) {
			lib.when(() -> LibraryUtils.resolveLibraryId(any(Repository.class), eq(ev), anyString())).thenReturn("L1");

			IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> generation.generateDatamart(study(), ev, List.of("NoSlashHere"), new Parameters(), "fallback"));
			assertTrue(ex.getMessage().contains("Subject"), "Must indicate subject id format issue");
		}
	}

	@Test
	void nestedEvidenceVariableNotFoundThrowsResourceNotFound() {
		EvidenceVariable ev = new EvidenceVariable().setUrl("http://example.org/ev/ERR2");
		addLibraryExtension(ev, "http://example.org/Library/L1");

		var c = new EvidenceVariable.EvidenceVariableCharacteristicComponent();
		var comb = new EvidenceVariable.EvidenceVariableCharacteristicDefinitionByCombinationComponent();
		comb.setCode(EvidenceVariable.CharacteristicCombination.ANYOF);
		EvidenceVariable.EvidenceVariableCharacteristicComponent nestedRef = new EvidenceVariable.EvidenceVariableCharacteristicComponent();
		nestedRef.setDefinitionCanonical("http://example.org/ev/UNKNOWN");
		comb.addCharacteristic(nestedRef);
		c.setDefinitionByCombination(comb);
		ev.addCharacteristic(c);

		try (MockedStatic<LibraryUtils> lib = mockStatic(LibraryUtils.class);
			  MockedStatic<EvidenceVariableUtils> evu = mockStatic(EvidenceVariableUtils.class)) {

			lib.when(() -> LibraryUtils.resolveLibraryId(any(Repository.class), eq(ev), anyString())).thenReturn("L1");
			evu.when(() -> EvidenceVariableUtils.resolveEvidenceVariable(any(Repository.class), eq("http://example.org/ev/UNKNOWN")))
				.thenThrow(new ResourceNotFoundException("not found"));

			assertThrows(ResourceNotFoundException.class,
				() -> generation.generateDatamart(study(), ev, List.of("Patient/5"), new Parameters(), "fallback"));
		}
	}

	@Test
	void emptySubjectsReturnsEmptyList() {
		EvidenceVariable ev = new EvidenceVariable().setUrl("http://example.org/ev/EV0");
		ev.addCharacteristic(expressionDef("A"));

		try (MockedStatic<LibraryUtils> lib = mockStatic(LibraryUtils.class)) {
			lib.when(() -> LibraryUtils.resolveLibraryId(any(Repository.class), eq(ev), anyString())).thenReturn("L1");

			ListResource list = generation.generateDatamart(study(), ev, List.of(), new Parameters(), "fallback");

			assertNotNull(list);
			assertEquals(0, list.getEntry().size());
			verify(repository, never()).create(any(Parameters.class));
		}
	}

	// -----------------------
	// Small utility helpers
	// -----------------------
	private ResearchStudy study() {
		return new ResearchStudy().setUrl("http://example.org/study/RS1");
	}

	private EvidenceVariable.EvidenceVariableCharacteristicComponent expressionDef(String exprName) {
		EvidenceVariable.EvidenceVariableCharacteristicComponent c = new EvidenceVariable.EvidenceVariableCharacteristicComponent();
		c.setDefinitionExpression(new Expression().setExpression(exprName));
		return c;
	}

	private Parameters outBoolean(String name, Boolean value) {
		Parameters p = new Parameters();
		p.addParameter().setName(name).setValue(value == null ? null : new BooleanType(value));
		return p;
	}

	private Parameters captureCreatedParameters() {
		ArgumentCaptor<Parameters> captor = ArgumentCaptor.forClass(Parameters.class);
		verify(repository, atLeastOnce()).create(captor.capture());
		return captor.getValue();
	}

	private MethodOutcome methodOutcome(String idPart) {
		MethodOutcome methodOutcome = new MethodOutcome();
		methodOutcome.setId(new IdType("Parameters", idPart));
		return methodOutcome;
	}

	private void stubPatientRead(String id) {
		Patient pat = new Patient();
		pat.addIdentifier(new Identifier().setSystem("urn:sys").setValue("real-" + id));
		when(repository.read(eq(Patient.class), argThat(x -> x != null && id.equals(x.getIdPart())))).thenReturn(pat);
	}

	private void addLibraryExtension(EvidenceVariable ev, String libraryCanonicalUrl) {
		ev.addExtension(new Extension(EXT_CQF_LIBRARY, new CanonicalType(libraryCanonicalUrl)));
	}
}
