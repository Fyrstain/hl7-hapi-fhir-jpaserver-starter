package ca.uhn.fhir.jpa.starter.generation;

import ca.uhn.fhir.jpa.starter.generation.provider.GenerateProvider;
import ca.uhn.fhir.jpa.starter.generation.provider.GenerationProperties;
import ca.uhn.fhir.jpa.starter.generation.service.GenerationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional({GenerationConfigCondition.class})
public class GenerationConfig {

	@Bean
	public GenerationService generationService(GenerationProperties properties) {
		return new GenerationService(properties);
	}

	@Bean
	public GenerateProvider generateProvider(GenerationService service) {
		return new GenerateProvider(service);
	}
}