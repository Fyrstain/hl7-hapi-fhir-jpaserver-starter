package ca.uhn.fhir.jpa.starter.generation.provider;

import ca.uhn.fhir.jpa.starter.generation.service.GenerationService;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.StringType;

public class GenerateProvider {

    private final GenerationService dolusGenerationService;

    public GenerateProvider(GenerationService generationService) {
        this.dolusGenerationService = generationService;
    }

    @Operation(name = "$generate", idempotent = false)
    public Bundle generate(
            @OperationParam(name = "realm") StringType realm,
            @OperationParam(name = "count") IntegerType count
    ) {
        return dolusGenerationService.generate(
                realm != null ? realm.getValue() : "fr",
                count != null ? count.getValue() : 10
        );
    }
}