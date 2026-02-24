package ca.uhn.fhir.jpa.starter.questionnaire.service;

import ca.uhn.fhir.repository.IRepository;
import org.hl7.fhir.instance.model.api.*;
import org.opencds.cqf.fhir.cql.LibraryEngine;
import org.opencds.cqf.fhir.cr.CrSettings;
import org.opencds.cqf.fhir.cr.common.IOperationProcessor;
import org.opencds.cqf.fhir.cr.questionnaireresponse.QuestionnaireResponseProcessor;
import org.opencds.cqf.fhir.cr.questionnaireresponse.extract.ExtractProcessor;
import org.opencds.cqf.fhir.cr.questionnaireresponse.extract.ExtractRequest;
import org.opencds.cqf.fhir.cr.questionnaireresponse.extract.IExtractProcessor;
import org.opencds.cqf.fhir.utility.monad.Either;

import java.util.List;

import static ca.uhn.fhir.jpa.starter.questionnaire.service.EnableWhenMassager.massageForDisabledQuestions;
import static ca.uhn.fhir.jpa.starter.questionnaire.service.NoAnswerMassager.massageForNoAnswers;

public class CustomQuestionnaireResponseProcessor extends QuestionnaireResponseProcessor {

	public CustomQuestionnaireResponseProcessor(IRepository repository) {
		super(repository, CrSettings.getDefault());
	}

	public CustomQuestionnaireResponseProcessor(
		IRepository repository,
		CrSettings crSettings,
		IExtractProcessor extractProcessor) {
		super(repository, crSettings, List.of((IOperationProcessor) extractProcessor));
	}

	@Override
	public <R extends IBaseResource> IBaseBundle extract(
		Either<IIdType, R> questionnaireResponseId,
		Either<IIdType, R> questionnaireId,
		IBaseParameters parameters,
		IBaseBundle data,
		LibraryEngine libraryEngine) {

		var questionnaireResponse = resolveQuestionnaireResponse(questionnaireResponseId);
		var questionnaire = resolveQuestionnaire(questionnaireResponse, questionnaireId);

		questionnaireResponse = (R) massageForDisabledQuestions(
			repository.fhirContext(), questionnaire, questionnaireResponse);
		questionnaireResponse = (R) massageForNoAnswers(
			repository.fhirContext(), questionnaire, questionnaireResponse);

		var subject = (IBaseReference) modelResolver.resolvePath(questionnaireResponse, "subject");

		var request = new ExtractRequest(
			questionnaireResponse,
			questionnaire,
			subject == null ? null : subject.getReferenceElement(),
			parameters,
			data,
			libraryEngine,
			modelResolver,
			null // IInputParameterResolver
		);

		var processor = extractProcessor != null ? extractProcessor : new ExtractProcessor();
		return processor.extract(request);
	}
}
