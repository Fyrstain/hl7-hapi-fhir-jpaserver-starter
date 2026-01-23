package ca.uhn.fhir.jpa.starter.questionnaire.provider.R5;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.annotations.OnR5Condition;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import org.hl7.fhir.r5.model.*;
import org.opencds.cqf.fhir.cr.questionnaire.QuestionnaireProcessor;
import org.opencds.cqf.fhir.utility.monad.Eithers;
import org.opencds.cqf.fhir.utility.repository.RestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Conditional(OnR5Condition.class)
public class PopulateProvider {

    @Value("${remote.url}")
    private String remoteUrl;

    @Autowired
    private FhirContext context;

    @Operation(name = "$populate", type = Questionnaire.class)
    public QuestionnaireResponse populate(@IdParam IdType id,
                                          @OperationParam(name = "canonical") String canonical,
                                          @OperationParam(name = "questionnaire") Questionnaire questionnaire,
                                          @OperationParam(name = "subject") String subject,
                                          @OperationParam(name = "parameters") Parameters parameters,
                                          @OperationParam(name = "bundle") Bundle bundle,
                                          @OperationParam(name = "dataEndpoint") Endpoint dataEndpoint,
                                          @OperationParam(name = "contentEndpoint") Endpoint contentEndpoint,
                                          @OperationParam(name = "terminologyEndpoint") Endpoint terminologyEndpoint) {
        StringType canonicalString = canonical != null ? new StringType(canonical) : null;
        return (QuestionnaireResponse) new QuestionnaireProcessor(
                new RestRepository(context.getRestfulClientFactory().newGenericClient(remoteUrl)))
                .populate(
                        Eithers.for3(canonicalString, id, questionnaire),
                        subject,
                        List.of(),
                        null,
                        bundle,
                        false,
                        dataEndpoint,
                        contentEndpoint,
                        terminologyEndpoint);
    }

    @Operation(name = "$populate", type = Questionnaire.class)
    public QuestionnaireResponse populate(@OperationParam(name = "canonical") String canonical,
                                          @OperationParam(name = "questionnaire") Questionnaire questionnaire,
                                          @OperationParam(name = "subject") String subject,
                                          @OperationParam(name = "parameters") Parameters parameters,
                                          @OperationParam(name = "bundle") Bundle bundle,
                                          @OperationParam(name = "dataEndpoint") Endpoint dataEndpoint,
                                          @OperationParam(name = "contentEndpoint") Endpoint contentEndpoint,
                                          @OperationParam(name = "terminologyEndpoint") Endpoint terminologyEndpoint) {
        StringType canonicalString = canonical != null ? new StringType(canonical) : null;
        return (QuestionnaireResponse) new QuestionnaireProcessor(
                new RestRepository(context.getRestfulClientFactory().newGenericClient(remoteUrl)))
                .populate(
                        Eithers.for3(canonicalString, null, questionnaire),
                        subject,
                        List.of(),
                        null,
                        bundle,
                        false,
                        dataEndpoint,
                        contentEndpoint,
                        terminologyEndpoint);
    }
}
