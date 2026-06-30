package ca.uhn.fhir.jpa.starter.generation;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GenerationConfigCondition implements Condition {

	@Override
	public boolean matches(ConditionContext theConditionContext, AnnotatedTypeMetadata theAnnotatedTypeMetadata) {
		String property = theConditionContext.getEnvironment().getProperty("hapi.fhir.generation.enabled");
		return Boolean.parseBoolean(property);
	}
}