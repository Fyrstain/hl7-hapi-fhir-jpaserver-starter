package ca.uhn.fhir.jpa.starter.questionnaire.provider.R5;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.QuestionnaireResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.opencds.cqf.fhir.cr.questionnaire.QuestionnaireProcessor;
import org.opencds.cqf.fhir.utility.repository.RestRepository;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class PopulateProviderTests {

	private static void setField(Object target, String fieldName, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.set(target, value);
	}

	@Test
	void populate_instanceLevel_doesNotFail_andBuildsInfrastructure() throws Exception {
		var provider = new PopulateProvider();
		setField(provider, "remoteUrl", "http://example.org/fhir");

		var context = mock(FhirContext.class);
		setField(provider, "context", context);

		var factory = mock(IRestfulClientFactory.class);
		when(context.getRestfulClientFactory()).thenReturn(factory);

		var client = mock(IGenericClient.class);
		when(factory.newGenericClient("http://example.org/fhir")).thenReturn(client);

		try (MockedConstruction<RestRepository> repoConstruction =
				  mockConstruction(RestRepository.class);
			  MockedConstruction<QuestionnaireProcessor> procConstruction =
				  mockConstruction(QuestionnaireProcessor.class)) {

			var id = new IdType("Questionnaire/123");

			QuestionnaireResponse result = provider.populate(
				id,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null
			);

			assertNull(result);
			assertEquals(1, repoConstruction.constructed().size());
			assertEquals(1, procConstruction.constructed().size());
			verify(factory).newGenericClient("http://example.org/fhir");
		}
	}
}
