package ca.uhn.fhir.jpa.starter.questionnaire.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.repository.IRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opencds.cqf.fhir.cr.CrSettings;
import org.opencds.cqf.fhir.cr.questionnaireresponse.extract.IExtractProcessor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class CustomQuestionnaireResponseProcessorTests {

	private IRepository mockRepository() {
		IRepository repo = Mockito.mock(IRepository.class);
		Mockito.when(repo.fhirContext()).thenReturn(FhirContext.forR4());
		return repo;
	}

	@Test
	void testConstructorWithRepositoryOnly() {
		IRepository repository = mockRepository();

		CustomQuestionnaireResponseProcessor processor =
			new CustomQuestionnaireResponseProcessor(repository);

		assertNotNull(processor);
	}

	@Test
	void testConstructorWithRepositorySettingsAndExtractProcessor() {
		IRepository repository = mockRepository();
		CrSettings settings = CrSettings.getDefault();
		IExtractProcessor extractProcessor = Mockito.mock(IExtractProcessor.class);

		CustomQuestionnaireResponseProcessor processor =
			new CustomQuestionnaireResponseProcessor(repository, settings, extractProcessor);

		assertNotNull(processor);
	}

	@Test
	void testConstructorDoesNotThrow() {
		IRepository repository = mockRepository();
		CrSettings settings = CrSettings.getDefault();
		IExtractProcessor extractProcessor = Mockito.mock(IExtractProcessor.class);

		assertDoesNotThrow(() ->
			new CustomQuestionnaireResponseProcessor(repository, settings, extractProcessor)
		);
	}
}
