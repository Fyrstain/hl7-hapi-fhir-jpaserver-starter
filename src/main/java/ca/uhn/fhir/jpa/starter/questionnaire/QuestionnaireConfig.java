package ca.uhn.fhir.jpa.starter.questionnaire;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.starter.questionnaire.provider.R4.PopulateProvider;
import ca.uhn.fhir.jpa.starter.questionnaire.provider.R4.ExtractProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional({QuestionnaireConfigCondition.class})
public class QuestionnaireConfig {
	 @Bean
	 public PopulateProvider populateProvider() {
		  return new PopulateProvider();
	 }

	 @Bean
	 public ExtractProvider extractProvider() {
		  return new ExtractProvider();
	 }
}
