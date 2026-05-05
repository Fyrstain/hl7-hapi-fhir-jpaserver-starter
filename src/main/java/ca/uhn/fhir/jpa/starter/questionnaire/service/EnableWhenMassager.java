package ca.uhn.fhir.jpa.starter.questionnaire.service;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r5.hapi.ctx.FhirR5;

import java.util.List;

import static ca.uhn.fhir.jpa.starter.questionnaire.util.QuestionnaireResponseUtil.getAnswers;
import static ca.uhn.fhir.jpa.starter.questionnaire.util.QuestionnaireResponseUtil.removeAnswerIfExists;

public class EnableWhenMassager {

    public static IBaseResource massageForDisabledQuestions(FhirContext context, IBaseResource questionnaire, IBaseResource questionnaireResponse) {
        if(context.getVersion() instanceof FhirR5) {
            //TODO
            return null;
        } else {
            return massageForDisabledQuestions((Questionnaire) questionnaire, (QuestionnaireResponse) questionnaireResponse);
        }
    }

    private static IBaseResource massageForDisabledQuestions(Questionnaire questionnaire, QuestionnaireResponse questionnaireResponse) {
        questionnaire.getItem().forEach(item -> processItem(item, questionnaireResponse));
        return questionnaireResponse;
    }

    private static void processItem(Questionnaire.QuestionnaireItemComponent item, QuestionnaireResponse questionnaireResponse) {
        if(item.hasEnableWhen()) {
            List<Questionnaire.QuestionnaireItemEnableWhenComponent> enableWhens = item.getEnableWhen();

            enableWhens.forEach(enableWhen -> {
                if (!isEnabled(enableWhen, questionnaireResponse)) {
                    removeAnswerIfExists(questionnaireResponse, item.getLinkId());
                }
            });
        }
        item.getItem().forEach(subItem -> processItem(subItem, questionnaireResponse));
    }

    private static boolean isEnabled(Questionnaire.QuestionnaireItemEnableWhenComponent enableWhen, QuestionnaireResponse questionnaireResponse) {
        if (Questionnaire.QuestionnaireItemOperator.EXISTS.equals(enableWhen.getOperator())) {
            List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
            if (enableWhen.getAnswerBooleanType().booleanValue()) {
                return !answers.isEmpty();
            } else {
                return answers.isEmpty();
            }
        } else if (Questionnaire.QuestionnaireItemOperator.NOT_EQUAL.equals(enableWhen.getOperator())) {
            List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
            return answers.stream().noneMatch(answer -> enableWhen.getAnswer().equalsShallow(answer.getValue()));
        } else if (Questionnaire.QuestionnaireItemOperator.EQUAL.equals(enableWhen.getOperator())) {
            List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
            return answers.stream().anyMatch(answer -> enableWhen.getAnswer().equalsShallow(answer.getValue()));
        } else if (Questionnaire.QuestionnaireItemOperator.LESS_THAN.equals(enableWhen.getOperator())) {
            if (enableWhen.hasAnswerDecimalType()) {
                List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
                return answers.stream().allMatch(answer -> enableWhen.getAnswerDecimalType().getValue().compareTo(answer.getValueDecimalType().getValue()) == 1);
            } else if (enableWhen.hasAnswerIntegerType()) {
                List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
                return answers.stream().allMatch(answer -> enableWhen.getAnswerIntegerType().getValue() > answer.getValueIntegerType().getValue());
            }
            return true;
        } else if (Questionnaire.QuestionnaireItemOperator.GREATER_THAN.equals(enableWhen.getOperator())) {
            if (enableWhen.hasAnswerDecimalType()) {
                List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
                return answers.stream().allMatch(answer -> enableWhen.getAnswerDecimalType().getValue().compareTo(answer.getValueDecimalType().getValue()) == -1);
            } else if (enableWhen.hasAnswerIntegerType()) {
                List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
                return answers.stream().allMatch(answer -> enableWhen.getAnswerIntegerType().getValue() < answer.getValueIntegerType().getValue());
            }
            return true;
        } else if (Questionnaire.QuestionnaireItemOperator.LESS_OR_EQUAL.equals(enableWhen.getOperator())) {
            if (enableWhen.hasAnswerDecimalType()) {
                List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
                return answers.stream().allMatch(answer -> enableWhen.getAnswerDecimalType().getValue().compareTo(answer.getValueDecimalType().getValue()) >= 0);
            } else if (enableWhen.hasAnswerIntegerType()) {
                List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
                return answers.stream().allMatch(answer -> enableWhen.getAnswerIntegerType().getValue() >= answer.getValueIntegerType().getValue());
            }
            return true;
        } else if (Questionnaire.QuestionnaireItemOperator.GREATER_OR_EQUAL.equals(enableWhen.getOperator())) {
            if (enableWhen.hasAnswerDecimalType()) {
                List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
                return answers.stream().allMatch(answer -> enableWhen.getAnswerDecimalType().getValue().compareTo(answer.getValueDecimalType().getValue()) <= 0);
            } else if (enableWhen.hasAnswerIntegerType()) {
                List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> answers = getAnswers(questionnaireResponse, enableWhen.getQuestion());
                return answers.stream().allMatch(answer -> enableWhen.getAnswerIntegerType().getValue() <= answer.getValueIntegerType().getValue());
            }
            return true;
        }
        return true;
    }
}
