package ca.uhn.fhir.jpa.starter.questionnaire.service;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r5.hapi.ctx.FhirR5;

import static ca.uhn.fhir.jpa.starter.questionnaire.util.QuestionnaireResponseUtil.removeAnswerIfExists;

public class NoAnswerMassager {

    public static IBaseResource massageForNoAnswers(FhirContext context, IBaseResource questionnaire, IBaseResource questionnaireResponse) {
        if (context.getVersion() instanceof FhirR5) {
            // TODO: Implement R5 logic
            return null;
        } else {
            return massageForNoAnswers((Questionnaire) questionnaire, (QuestionnaireResponse) questionnaireResponse);
        }
    }

    private static QuestionnaireResponse massageForNoAnswers(Questionnaire questionnaire, QuestionnaireResponse questionnaireResponse) {
        questionnaireResponse.getItem().forEach(qrItem -> processQRItem(questionnaireResponse, qrItem));
        return questionnaireResponse;
    }

    private static void processQRItem(QuestionnaireResponse questionnaireResponse, QuestionnaireResponseItemComponent qrItem) {
        if (qrItem.hasItem()) {
            for (QuestionnaireResponseItemComponent child : qrItem.getItem()) {
                processQRItem(questionnaireResponse, child);
            }
        }
        if (!hasRealAnswerDeep(qrItem)) {
            removeAnswerIfExists(questionnaireResponse, qrItem.getLinkId());
        }
    }


    private static boolean hasRealAnswerDeep(QuestionnaireResponseItemComponent item) {
        if (item.hasAnswer()) {
            for (QuestionnaireResponseItemAnswerComponent answer : item.getAnswer()) {
                if (answer.hasValueQuantity()) {
                    Quantity qty = answer.getValueQuantity();
                    if (qty.getValue() != null) {
                        return true;
                    }
                } else {
                    if (answer.hasValue() || answer.hasItem()) {
                        return true;
                    }
                }
            }
        }
        if (item.hasItem()) {
            for (QuestionnaireResponseItemComponent child : item.getItem()) {
                if (hasRealAnswerDeep(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
