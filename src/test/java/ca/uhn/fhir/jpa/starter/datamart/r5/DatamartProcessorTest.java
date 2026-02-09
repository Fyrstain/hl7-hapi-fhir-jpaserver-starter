package ca.uhn.fhir.jpa.starter.datamart.r5;

import ca.uhn.fhir.jpa.starter.datamart.service.r5.service.DatamartGeneration;
import ca.uhn.fhir.jpa.starter.datamart.service.r5.service.DatamartProcessor;
import ca.uhn.fhir.jpa.starter.datamart.service.r5.utils.ResearchStudyUtils;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r5.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.opencds.cqf.fhir.api.Repository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatamartProcessorTest {

	private Repository repository;
	private DatamartProcessor processor;

	@BeforeEach
	void setUp() {
		repository = mock(Repository.class);
		processor = new DatamartProcessor(repository, /* cqlClient = */ null);
	}

	@Test
	void generateDatamartWithDerivedLibraryId() {
		ResearchStudy researchStudy = study("http://example.org/researchStudy");
		EvidenceVariable ev = evWithLibrary("http://example.org/ev", "http://example.org/Library/LibA|1.0.0");
		Group group = eligibleGroupWithSubjects("p1", "p2");
		ListResource listOut = new ListResource();
		listOut.setId("List/out");

		try (MockedStatic<ResearchStudyUtils> utils = Mockito.mockStatic(ResearchStudyUtils.class);
			  MockedConstruction<DatamartGeneration> genCtor = Mockito.mockConstruction(
				  DatamartGeneration.class,
				  (mock, ctx) -> when(mock.generateDatamart(any(), any(), anyList(), any(), any()))
					  .thenReturn(listOut)
			  )) {

			utils.when(() -> ResearchStudyUtils.getEligibleGroup(eq(researchStudy), eq(repository))).thenReturn(group);
			utils.when(() -> ResearchStudyUtils.getEvidenceVariable(eq(researchStudy), eq(repository))).thenReturn(ev);
			utils.when(() -> ResearchStudyUtils.getSubjectReferences(eq(group), eq(repository)))
				.thenReturn(List.of("Patient/1", "Patient/2"));

			when(repository.search(eq(Bundle.class), eq(Library.class), any(), isNull()))
				.thenReturn(bundleWith(new Library().setId("LibA")));

			Parameters evalParams = new Parameters();
			ListResource out = processor.generateDatamart(researchStudy, evalParams);

			assertNotNull(out);
			assertEquals("out", out.getIdElement().getIdPart());
		}
	}

	@Test
	void generateDatamartLibraryNotFoundThrows404() {
		ResearchStudy researchStudy = study("http://example.org/researchStudy");
		EvidenceVariable evidenceVariable = evWithLibrary("http://example.org/ev", "http://example.org/Library/Missing|0.0.1");
		Group group = eligibleGroupWithSubjects("p1");

		try (MockedStatic<ResearchStudyUtils> utils = Mockito.mockStatic(ResearchStudyUtils.class)) {
			utils.when(() -> ResearchStudyUtils.getEligibleGroup(eq(researchStudy), eq(repository))).thenReturn(group);
			utils.when(() -> ResearchStudyUtils.getEvidenceVariable(eq(researchStudy), eq(repository))).thenReturn(evidenceVariable);

			when(repository.search(eq(Bundle.class), eq(Library.class), any(), isNull()))
				.thenReturn(new Bundle());

			assertThrows(ResourceNotFoundException.class,
				() -> processor.generateDatamart(researchStudy, new Parameters()));
		}
	}

	/**
	 * Without a cqf-library extension, the fallback library id should be null and we still delegate.
	 */
	@Test
	void generateDatamart_NoLibraryExtension_PassesNullFallback() {
		ResearchStudy researchStudy = study("http://example.org/researchStudy");
		EvidenceVariable evidenceVariable = new EvidenceVariable().setUrl("http://example.org/ev");
		Group group = eligibleGroupWithSubjects("p1");
		ListResource listOut = new ListResource();
		listOut.setId("List/out2");

		try (MockedStatic<ResearchStudyUtils> utils = Mockito.mockStatic(ResearchStudyUtils.class);
			  MockedConstruction<DatamartGeneration> genCtor = Mockito.mockConstruction(
				  DatamartGeneration.class,
				  (mock, ctx) -> when(mock.generateDatamart(any(), any(), anyList(), any(), isNull()))
					  .thenReturn(listOut)
			  )) {

			utils.when(() -> ResearchStudyUtils.getEligibleGroup(eq(researchStudy), eq(repository))).thenReturn(group);
			utils.when(() -> ResearchStudyUtils.getEvidenceVariable(eq(researchStudy), eq(repository))).thenReturn(evidenceVariable);
			utils.when(() -> ResearchStudyUtils.getSubjectReferences(eq(group), eq(repository)))
				.thenReturn(List.of("Patient/1"));

			ListResource out = processor.generateDatamart(researchStudy, new Parameters());
			assertEquals("out2", out.getIdElement().getIdPart());

			DatamartGeneration constructed = genCtor.constructed().get(0);
			verify(constructed).generateDatamart(eq(researchStudy), eq(evidenceVariable), eq(List.of("Patient/1")), any(Parameters.class), isNull());
		}
	}

	private static ResearchStudy study(String url) {
		ResearchStudy researchStudy = new ResearchStudy();
		researchStudy.setUrl(url);
		return researchStudy;
	}

	private static EvidenceVariable evWithLibrary(String url, String libraryCanonical) {
		EvidenceVariable evidenceVariable = new EvidenceVariable();
		evidenceVariable.setUrl(url);
		evidenceVariable.addExtension(new Extension("http://hl7.org/fhir/StructureDefinition/cqf-library",
			new CanonicalType(libraryCanonical)));
		return evidenceVariable;
	}

	private static Group eligibleGroupWithSubjects(String... ids) {
		Group group = new Group();
		for (String id : ids) {
			group.addMember().setEntity(new Reference().setIdentifier(new Identifier().setSystem("s").setValue(id)));
		}
		return group;
	}

	private static Bundle bundleWith(Resource r) {
		Bundle bundle = new Bundle();
		bundle.addEntry().setResource(r);
		return bundle;
	}

}
