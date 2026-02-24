package ca.uhn.fhir.jpa.starter.questionnaire.util;

import org.hl7.fhir.r4.model.QuestionnaireResponse;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class QuestionnaireResponseUtil {

    public static List<QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent> getAnswers(QuestionnaireResponse questionnaireResponse, String question) {
        return questionnaireResponse.getItem()
                .stream().map(item -> getItemOrChild(item, question))
                .filter(Objects::nonNull)
                .map(QuestionnaireResponse.QuestionnaireResponseItemComponent::getAnswer)
                .findFirst()
                .orElse(List.of());
    }

    private static QuestionnaireResponse.QuestionnaireResponseItemComponent getItemOrChild(QuestionnaireResponse.QuestionnaireResponseItemComponent item, String question) {
        if (question.equals(item.getLinkId())) {
            return item;
        }
        return item.getItem().stream()
                .map(subItem -> getItemOrChild(subItem, question))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }


    public static void removeAnswerIfExists(QuestionnaireResponse questionnaireResponse, String question) {
        questionnaireResponse.setItem(
            questionnaireResponse.getItem().stream()
                .filter(item -> !question.equals(item.getLinkId()))
                .map(item -> removeChild(item, question))
                .collect(Collectors.toList()));
    }

    private static QuestionnaireResponse.QuestionnaireResponseItemComponent removeChild(QuestionnaireResponse.QuestionnaireResponseItemComponent item, String question) {
        item.setItem(
                item.getItem().stream()
                        .filter(subItem -> !question.equals(subItem.getLinkId()))
                        .map(subItem -> removeChild(subItem, question))
                        .collect(Collectors.toList()));
        return item;
    }
}
