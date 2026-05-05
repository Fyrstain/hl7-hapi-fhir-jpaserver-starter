package ca.uhn.fhir.jpa.starter.questionnaire.provider.R4;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.questionnaire.service.CustomQuestionnaireResponseProcessor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.opencds.cqf.fhir.utility.monad.Either;
import org.opencds.cqf.fhir.utility.repository.RestRepository;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtractProviderTests {

	private static void setField(Object target, String fieldName, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static Object getEitherField(Object either, String fieldName) throws Exception {
		Field f = either.getClass().getDeclaredField(fieldName);
		f.setAccessible(true);
		return f.get(either);
	}

	@Test
	void extract_instanceLevel_buildsRestClient_andCallsProcessor_withEitherLeftId() throws Exception {
		var provider = new ExtractProvider();
		setField(provider, "remoteUrl", "http://example.org/fhir");
		var context = mock(FhirContext.class);
		setField(provider, "context", context);

		var factory = mock(IRestfulClientFactory.class);
		when(context.getRestfulClientFactory()).thenReturn(factory);

		var client = mock(IGenericClient.class);
		when(factory.newGenericClient("http://example.org/fhir")).thenReturn(client);

		var expectedBundle = new Bundle();
		expectedBundle.setType(Bundle.BundleType.TRANSACTION);

		try (MockedConstruction<RestRepository> repoConstruction =
				  mockConstruction(RestRepository.class, (mock, ctx) -> {
				  });
			  MockedConstruction<CustomQuestionnaireResponseProcessor> procConstruction =
				  mockConstruction(CustomQuestionnaireResponseProcessor.class, (mock, ctx) -> {
					  when(mock.extract(any())).thenReturn(expectedBundle);
				  })) {

			var id = new IdType("QuestionnaireResponse/123");

			IBaseBundle result = provider.extract(id);

			assertSame(expectedBundle, result);

			verify(factory).newGenericClient("http://example.org/fhir");
			assertEquals(1, repoConstruction.constructed().size(), "RestRepository must be built once");
			assertEquals(1, procConstruction.constructed().size(), "Processor must be built once");

			var processorMock = procConstruction.constructed().get(0);
			ArgumentCaptor<Either> captor = ArgumentCaptor.forClass(Either.class);
			verify(processorMock).extract(captor.capture());

			Either either = captor.getValue();
			Object left = getEitherField(either, "left");
			Object right = getEitherField(either, "right");

			assertEquals(id, left);
			assertNull(right);
		}
	}

	@Test
	void extract_typeLevel_buildsRestClient_andCallsProcessor_withEitherRightQuestionnaireResponse() throws Exception {
		var provider = new ExtractProvider();
		setField(provider, "remoteUrl", "http://example.org/fhir");
		var context = mock(FhirContext.class);
		setField(provider, "context", context);

		var factory = mock(IRestfulClientFactory.class);
		when(context.getRestfulClientFactory()).thenReturn(factory);

		var client = mock(IGenericClient.class);
		when(factory.newGenericClient("http://example.org/fhir")).thenReturn(client);

		var expectedBundle = new Bundle();
		expectedBundle.setType(Bundle.BundleType.TRANSACTION);

		try (MockedConstruction<RestRepository> repoConstruction =
				  mockConstruction(RestRepository.class);
			  MockedConstruction<CustomQuestionnaireResponseProcessor> procConstruction =
				  mockConstruction(CustomQuestionnaireResponseProcessor.class, (mock, ctx) -> {
					  when(mock.extract(any())).thenReturn(expectedBundle);
				  })) {

			var qr = new QuestionnaireResponse();
			qr.setId("QuestionnaireResponse/ABC");

			IBaseBundle result = provider.extract(qr);

			assertSame(expectedBundle, result);

			verify(factory).newGenericClient("http://example.org/fhir");
			assertEquals(1, repoConstruction.constructed().size(), "RestRepository must be built once");
			assertEquals(1, procConstruction.constructed().size(), "Processor must be built once");

			var processorMock = procConstruction.constructed().get(0);
			ArgumentCaptor<Either> captor = ArgumentCaptor.forClass(Either.class);
			verify(processorMock).extract(captor.capture());

			Either either = captor.getValue();
			Object left = getEitherField(either, "left");
			Object right = getEitherField(either, "right");

			assertNull(left);
			assertSame(qr, right);
		}
	}
}
