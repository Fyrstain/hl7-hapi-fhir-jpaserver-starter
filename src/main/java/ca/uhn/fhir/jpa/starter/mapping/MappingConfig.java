package ca.uhn.fhir.jpa.starter.mapping;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.starter.mapping.provider.TransformProvider;
import ca.uhn.fhir.jpa.starter.mapping.service.MatchboxTransformService;
import ch.ahdis.matchbox.engine.CdaMappingEngine;
import ch.ahdis.matchbox.engine.MatchboxEngine;
import org.hl7.fhir.r4.model.StructureMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URISyntaxException;

@Configuration
@Conditional({MappingConfigCondition.class})
public class MappingConfig {

	@Bean
	public MatchboxEngine matchboxEngine() throws IOException, URISyntaxException {
		return new CdaMappingEngine.CdaMappingEngineBuilder().getCdaEngineR4();
	}

	@Bean
	public MatchboxTransformService matchboxTransformService(FhirContext fhirContext, MatchboxEngine matchboxEngine) {
		return new MatchboxTransformService(fhirContext, matchboxEngine);
	}

	@Bean
	public TransformProvider transformOperationProvider(IFhirResourceDao<StructureMap> structureMapDao,
																 MatchboxTransformService matchboxTransformService) {
		return new TransformProvider(structureMapDao, matchboxTransformService);
	}
}
