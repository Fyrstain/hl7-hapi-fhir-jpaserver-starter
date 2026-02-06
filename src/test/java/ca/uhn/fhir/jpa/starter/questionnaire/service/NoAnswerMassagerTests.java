package ca.uhn.fhir.jpa.starter.questionnaire.service;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoAnswerMassagerTests {

	@Test
	void massageForNoAnswers_removesEmptyAnswer() {
		var ctx = FhirContext.forR4();
		var questionnaire = new Questionnaire();
		var qr = new QuestionnaireResponse();
		var item = new QuestionnaireResponseItemComponent();
		item.setLinkId("q1");
		var answer = new QuestionnaireResponseItemAnswerComponent();
		answer.setValue(new Quantity());

		item.addAnswer(answer);
		qr.addItem(item);

		var result = NoAnswerMassager.massageForNoAnswers(ctx, questionnaire, qr);
		QuestionnaireResponse out = (QuestionnaireResponse) result;
		assertTrue(out.getItem().isEmpty(),
			"Empty answer must be deleted");
	}

	@Test
	void massageForNoAnswers_keepsQuantityWithValue() {
		var ctx = FhirContext.forR4();
		var questionnaire = new Questionnaire();
		var qr = new QuestionnaireResponse();
		var item = new QuestionnaireResponseItemComponent();
		item.setLinkId("q2");
		var answer = new QuestionnaireResponseItemAnswerComponent();
		Quantity q = new Quantity();
		q.setValue(42);

		answer.setValue(q);

		item.addAnswer(answer);
		qr.addItem(item);

		var result = NoAnswerMassager.massageForNoAnswers(ctx, questionnaire, qr);
		QuestionnaireResponse out = (QuestionnaireResponse) result;
		assertEquals(1, out.getItem().get(0).getAnswer().size(),
			"Answer Quantity with value must remain");
	}

	@Test
	void massageForNoAnswers_keepsNonQuantityAnswer() {
		var ctx = FhirContext.forR4();
		var questionnaire = new Questionnaire();
		var qr = new QuestionnaireResponse();
		var item = new QuestionnaireResponseItemComponent();
		item.setLinkId("q3");
		var answer = new QuestionnaireResponseItemAnswerComponent();
		answer.setValue(new org.hl7.fhir.r4.model.StringType("hello"));

		item.addAnswer(answer);
		qr.addItem(item);

		var result = NoAnswerMassager.massageForNoAnswers(ctx, questionnaire, qr);
		QuestionnaireResponse out = (QuestionnaireResponse) result;
		assertFalse(out.getItem().get(0).getAnswer().isEmpty(),
			"Answer String must remain");
	}

	@Test
	void massageForNoAnswers_removesItemWithoutAnswerDeep() {
		var ctx = FhirContext.forR4();
		var questionnaire = new Questionnaire();
		var qr = new QuestionnaireResponse();
		var parent = new QuestionnaireResponseItemComponent();
		parent.setLinkId("parent");
		var child = new QuestionnaireResponseItemComponent();
		child.setLinkId("child");

		parent.addItem(child);
		qr.addItem(parent);

		var result = NoAnswerMassager.massageForNoAnswers(ctx, questionnaire, qr);
		QuestionnaireResponse out = (QuestionnaireResponse) result;
		assertTrue(out.getItem().isEmpty(),
			"Child without a real answer must be deleted");
	}

	@Test
	void massageForNoAnswers_keepsParentIfChildHasRealAnswer() {
		var ctx = FhirContext.forR4();
		var questionnaire = new Questionnaire();
		var qr = new QuestionnaireResponse();
		var parent = new QuestionnaireResponseItemComponent();
		parent.setLinkId("parent");
		var child = new QuestionnaireResponseItemComponent();
		child.setLinkId("child");
		var answer = new QuestionnaireResponseItemAnswerComponent();
		answer.setValue(new org.hl7.fhir.r4.model.StringType("OK"));

		child.addAnswer(answer);
		parent.addItem(child);
		qr.addItem(parent);

		var result = NoAnswerMassager.massageForNoAnswers(ctx, questionnaire, qr);
		QuestionnaireResponse out = (QuestionnaireResponse) result;
		assertEquals(1, out.getItem().get(0).getItem().size(),
			"Child with real answer must remain");
	}

	@Test
	void massageForNoAnswers_r5ReturnsNull() {
		var ctx = FhirContext.forR5();
		var result =
			NoAnswerMassager.massageForNoAnswers(ctx, null, null);
		assertNull(result, "In R5, the method returns null because TODO is not implemented");
	}
}
