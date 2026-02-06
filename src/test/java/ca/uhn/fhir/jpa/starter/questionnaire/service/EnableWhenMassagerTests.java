package ca.uhn.fhir.jpa.starter.questionnaire.service;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r5.hapi.ctx.FhirR5;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class EnableWhenMassagerTests {

	private static Questionnaire.QuestionnaireItemComponent qItemWithLinkId(String linkId) {
		return new Questionnaire.QuestionnaireItemComponent().setLinkId(linkId);
	}

	private static QuestionnaireResponse.QuestionnaireResponseItemComponent qrItemWithStringAnswer(String linkId, String value) {
		var item = new QuestionnaireResponse.QuestionnaireResponseItemComponent().setLinkId(linkId);
		item.addAnswer().setValue(new StringType(value));
		return item;
	}

	private static QuestionnaireResponse.QuestionnaireResponseItemComponent qrItemWithIntegerAnswer(String linkId, int value) {
		var item = new QuestionnaireResponse.QuestionnaireResponseItemComponent().setLinkId(linkId);
		item.addAnswer().setValue(new IntegerType(value));
		return item;
	}

	private static QuestionnaireResponse.QuestionnaireResponseItemComponent qrItemWithDecimalAnswer(String linkId, String value) {
		var item = new QuestionnaireResponse.QuestionnaireResponseItemComponent().setLinkId(linkId);
		item.addAnswer().setValue(new DecimalType(new BigDecimal(value)));
		return item;
	}

	private static QuestionnaireResponse.QuestionnaireResponseItemComponent qrItemEmptyAnswer(String linkId) {
		var item = new QuestionnaireResponse.QuestionnaireResponseItemComponent().setLinkId(linkId);
		item.addAnswer(); // no value
		return item;
	}

	private static Questionnaire.QuestionnaireItemEnableWhenComponent enableWhenExists(String questionLinkId, boolean shouldExist) {
		var ew = new Questionnaire.QuestionnaireItemEnableWhenComponent();
		ew.setQuestion(questionLinkId);
		ew.setOperator(Questionnaire.QuestionnaireItemOperator.EXISTS);
		ew.setAnswer(new BooleanType(shouldExist));
		return ew;
	}

	private static Questionnaire.QuestionnaireItemEnableWhenComponent enableWhenEqual(String questionLinkId, String expected) {
		var ew = new Questionnaire.QuestionnaireItemEnableWhenComponent();
		ew.setQuestion(questionLinkId);
		ew.setOperator(Questionnaire.QuestionnaireItemOperator.EQUAL);
		ew.setAnswer(new StringType(expected));
		return ew;
	}

	private static Questionnaire.QuestionnaireItemEnableWhenComponent enableWhenNotEqual(String questionLinkId, String expected) {
		var ew = new Questionnaire.QuestionnaireItemEnableWhenComponent();
		ew.setQuestion(questionLinkId);
		ew.setOperator(Questionnaire.QuestionnaireItemOperator.NOT_EQUAL);
		ew.setAnswer(new StringType(expected));
		return ew;
	}

	private static Questionnaire.QuestionnaireItemEnableWhenComponent enableWhenLessThanDecimal(String questionLinkId, String threshold) {
		var ew = new Questionnaire.QuestionnaireItemEnableWhenComponent();
		ew.setQuestion(questionLinkId);
		ew.setOperator(Questionnaire.QuestionnaireItemOperator.LESS_THAN);
		ew.setAnswer(new DecimalType(new BigDecimal(threshold)));
		return ew;
	}

	private static Questionnaire.QuestionnaireItemEnableWhenComponent enableWhenGreaterThanInteger(String questionLinkId, int threshold) {
		var ew = new Questionnaire.QuestionnaireItemEnableWhenComponent();
		ew.setQuestion(questionLinkId);
		ew.setOperator(Questionnaire.QuestionnaireItemOperator.GREATER_THAN);
		ew.setAnswer(new IntegerType(threshold));
		return ew;
	}

	private static QuestionnaireResponse.QuestionnaireResponseItemComponent findQrItem(QuestionnaireResponse qr, String linkId) {
		for (var top : qr.getItem()) {
			var found = findQrItemRec(top, linkId);
			if (found != null) return found;
		}
		return null;
	}

	private static QuestionnaireResponse.QuestionnaireResponseItemComponent findQrItemRec(
		QuestionnaireResponse.QuestionnaireResponseItemComponent current, String linkId) {
		if (linkId.equals(current.getLinkId())) return current;
		for (var child : current.getItem()) {
			var found = findQrItemRec(child, linkId);
			if (found != null) return found;
		}
		return null;
	}

	@Test
	void massageForDisabledQuestions_whenR5_returnsNull() {
		var context = org.mockito.Mockito.mock(FhirContext.class);
		org.mockito.Mockito.when(context.getVersion()).thenReturn(new FhirR5());

		IBaseResource out = EnableWhenMassager.massageForDisabledQuestions(context, null, null);
		assertNull(out, "R5 branch is TODO and must return null");
	}

	@Test
	void exists_true_whenNoAnswers_removesAnswerOnTargetItem() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTrigger"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenExists("qTrigger", true));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithStringAnswer("qTarget", "will be removed"));

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outTarget = findQrItem(out, "qTarget");
		assertNull(outTarget, "qTarget answers must be removed when EXISTS(true) but no trigger answers");
	}

	@Test
	void exists_true_whenAnswersPresent_keepsAnswerOnTargetItem() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTrigger"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenExists("qTrigger", true));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithStringAnswer("qTrigger", "anything"));
		qr.addItem(qrItemWithStringAnswer("qTarget", "kept"));

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outTarget = findQrItem(out, "qTarget");
		assertNotNull(outTarget);
		assertFalse(outTarget.getAnswer().isEmpty(), "qTarget answers must be kept when EXISTS(true) and trigger has answers");
	}

	@Test
	void exists_false_whenAnswersPresent_removesAnswerOnTargetItem() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTrigger"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenExists("qTrigger", false));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithStringAnswer("qTrigger", "present"));
		qr.addItem(qrItemWithStringAnswer("qTarget", "removed"));

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outTarget = findQrItem(out, "qTarget");
		assertNull(outTarget, "qTarget answers must be removed when EXISTS(false) but trigger has answers");
	}

	@Test
	void equal_whenNoMatch_removesAnswer() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTrigger"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenEqual("qTrigger", "YES"));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithStringAnswer("qTrigger", "NO"));
		qr.addItem(qrItemWithStringAnswer("qTarget", "removed"));

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outTarget = findQrItem(out, "qTarget");
		assertNull(outTarget, "EQUAL must remove when no answer equals expected");
	}

	@Test
	void equal_whenMatch_keepsAnswer() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTrigger"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenEqual("qTrigger", "YES"));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithStringAnswer("qTrigger", "YES"));
		qr.addItem(qrItemWithStringAnswer("qTarget", "kept"));

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outTarget = findQrItem(out, "qTarget");
		assertNotNull(outTarget);
		assertFalse(outTarget.getAnswer().isEmpty(), "EQUAL must keep when any trigger answer matches expected");
	}

	@Test
	void notEqual_whenAnyEquals_removesAnswer() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTrigger"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenNotEqual("qTrigger", "BAD"));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithStringAnswer("qTrigger", "BAD"));
		qr.addItem(qrItemWithStringAnswer("qTarget", "removed"));

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outTarget = findQrItem(out, "qTarget");
		assertNull(outTarget, "NOT_EQUAL must remove when any trigger answer equals forbidden value");
	}

	@Test
	void notEqual_whenNoneEquals_keepsAnswer() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTrigger"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenNotEqual("qTrigger", "BAD"));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithStringAnswer("qTrigger", "OK"));
		qr.addItem(qrItemWithStringAnswer("qTarget", "kept"));

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outTarget = findQrItem(out, "qTarget");
		assertNotNull(outTarget);
		assertFalse(outTarget.getAnswer().isEmpty(), "NOT_EQUAL must keep when no trigger answer equals forbidden value");
	}

	@Test
	void lessThan_decimal_whenConditionFalse_removesAnswer() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTriggerDec"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenLessThanDecimal("qTriggerDec", "5"));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithDecimalAnswer("qTriggerDec", "6"));
		qr.addItem(qrItemWithStringAnswer("qTarget", "removed"));

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outTarget = findQrItem(out, "qTarget");
		assertNull(outTarget, "LESS_THAN(decimal) must remove when threshold is not > answer");
	}

	@Test
	void greaterThan_integer_whenConditionFalse_removesAnswer() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTriggerInt"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenGreaterThanInteger("qTriggerInt", 5));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithIntegerAnswer("qTriggerInt", 4));
		qr.addItem(qrItemWithStringAnswer("qTarget", "removed"));

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outTarget = findQrItem(out, "qTarget");
		assertNull(outTarget, "GREATER_THAN(integer) must remove when threshold is not < answer");
	}

	@Test
	void recursion_subItem_enableWhen_appliesAndRemovesOnlySubItemAnswer() {
		var q = new Questionnaire();
		var parent = qItemWithLinkId("parent");
		var trigger = qItemWithLinkId("qTrigger");
		var sub = qItemWithLinkId("subTarget");
		sub.addEnableWhen(enableWhenEqual("qTrigger", "YES"));

		parent.addItem(sub);
		q.addItem(trigger);
		q.addItem(parent);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemWithStringAnswer("qTrigger", "NO"));
		var qrParent = new QuestionnaireResponse.QuestionnaireResponseItemComponent().setLinkId("parent");
		qrParent.addItem(qrItemWithStringAnswer("subTarget", "removed"));
		qr.addItem(qrParent);

		var out = (QuestionnaireResponse) EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr);

		var outSub = findQrItem(out, "subTarget");
		assertNull(outSub, "subTarget answer must be removed via recursion");

		var outTrigger = findQrItem(out, "qTrigger");
		assertNotNull(outTrigger);
		assertFalse(outTrigger.getAnswer().isEmpty(), "trigger answer must remain");
	}

	@Test
	void exists_true_withEmptyAnswerObject_countsAsAnswerPresentOrNot_dependsOnUtil() {
		var q = new Questionnaire();
		q.addItem(qItemWithLinkId("qTrigger"));
		var target = qItemWithLinkId("qTarget");
		target.addEnableWhen(enableWhenExists("qTrigger", true));
		q.addItem(target);

		var qr = new QuestionnaireResponse();
		qr.addItem(qrItemEmptyAnswer("qTrigger"));
		qr.addItem(qrItemWithStringAnswer("qTarget", "maybe kept/maybe removed"));

		assertDoesNotThrow(() ->
			EnableWhenMassager.massageForDisabledQuestions(FhirContext.forR4(), q, qr));
	}
}
