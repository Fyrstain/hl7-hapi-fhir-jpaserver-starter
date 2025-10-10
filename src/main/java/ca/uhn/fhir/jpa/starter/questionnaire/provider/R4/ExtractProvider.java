package ca.uhn.fhir.jpa.starter.questionnaire.provider.R4;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.jpa.starter.questionnaire.service.CustomQuestionnaireResponseProcessor;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.opencds.cqf.fhir.utility.monad.Eithers;
import org.opencds.cqf.fhir.utility.repository.RestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OnR4Condition.class)
public class ExtractProvider {

    @Value("${remote.url}")
    private String remoteUrl;

    @Autowired
    private FhirContext context;

    /**
     * Implements the $extract operation on Instance Level.
     *
     * @param id the id of the QuestionnaireResponse to extract data from.
     * @return The resulting FHIR resource produced after extracting data. This will either be a single resource or a Transaction Bundle that contains multiple resources.
     */
    @Operation(name = "$extract", type = QuestionnaireResponse.class)
    public IBaseBundle extract(@IdParam IdType id) {
        return new CustomQuestionnaireResponseProcessor(
                new RestRepository(context.getRestfulClientFactory().newGenericClient(remoteUrl)))
                .extract(Eithers.for2(id, null));
    }

    /**
     * Implements the $extract operation on type Level.
     *
     * @param questionnaireResponse the QuestionnaireResponse to extract data from.
     * @return The resulting FHIR resource produced after extracting data. This will either be a single resource or a Transaction Bundle that contains multiple resources.
     */
    @Operation(name = "$extract", type = QuestionnaireResponse.class)
    public IBaseBundle extract(@OperationParam(name = "questionnaire-response") QuestionnaireResponse questionnaireResponse) {
        return new CustomQuestionnaireResponseProcessor(
                new RestRepository(context.getRestfulClientFactory().newGenericClient(remoteUrl)))
                .extract(Eithers.for2(null, questionnaireResponse));
    }

}
