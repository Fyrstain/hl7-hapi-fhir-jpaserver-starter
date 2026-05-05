package ca.uhn.fhir.jpa.starter.questionnaire;

import ca.uhn.fhir.jpa.starter.questionnaire.provider.R4.ExtractProvider;
import ca.uhn.fhir.jpa.starter.questionnaire.provider.R4.PopulateProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionnaireConfigTests {

	@Test
	void populateProviderBean_isCreated() {
		QuestionnaireConfig config = new QuestionnaireConfig();

		PopulateProvider provider = config.populateProvider();

		assertNotNull(provider);
	}

	@Test
	void extractProviderBean_isCreated() {
		QuestionnaireConfig config = new QuestionnaireConfig();

		ExtractProvider provider = config.extractProvider();

		assertNotNull(provider);
	}

	@Test
	void questionnaireConfigCondition_returnsFalse_whenPropertyMissing() {
		QuestionnaireConfigCondition condition = new QuestionnaireConfigCondition();

		ConditionContext context = mock(ConditionContext.class);

		Environment env = mock(Environment.class);
		when(context.getEnvironment()).thenReturn(env);

		when(env.getProperty("questionnaire.config.enabled")).thenReturn(null);

		boolean result = condition.matches(context, mock(AnnotatedTypeMetadata.class));

		assertFalse(result);
	}
}
