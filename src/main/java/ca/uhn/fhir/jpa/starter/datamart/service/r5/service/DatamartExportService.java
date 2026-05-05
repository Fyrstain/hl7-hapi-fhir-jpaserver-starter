package ca.uhn.fhir.jpa.starter.datamart.service.r5.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.datamart.service.Repositories;
import ca.uhn.fhir.jpa.starter.datamart.service.r5.impl.DatamartExportServiceImpl;
import ca.uhn.fhir.jpa.starter.datamart.service.r5.model.ExportType;
import ca.uhn.fhir.jpa.starter.datamart.service.r5.utils.ResearchStudyUtils;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r5.model.*;
import org.opencds.cqf.fhir.api.Repository;
import org.opencds.cqf.fhir.utility.search.Searches;

import java.util.Collection;
import java.util.Objects;

public class DatamartExportService implements DatamartExportServiceImpl {

	private final Repository repository;

	public DatamartExportService(Repository repository) {
		this.repository = repository;
	}

	/**
	 * Exports a datamart as a FHIR Binary resource for the specified ResearchStudy.
	 *
	 * @param researchStudyUrl      the canonical URL of the ResearchStudy to export
	 * @param researchStudyEndpoint the FHIR Endpoint containing the ResearchStudy,
	 *                              associated CQL libraries, and inclusion criteria
	 * @param structureMapEndpoint  the FHIR Endpoint used to resolve StructureMap resources
	 *                              referenced in the transformation
	 * @param terminologyEndpoint   (optional) the FHIR Endpoint used to resolve terminology
	 *                              resources (ValueSet, CodeSystem) referenced in CQL
	 * @param remoteEndpoint
	 * @param type                  the desired export MIME type (e.g. "text/csv" or "rest" or "bulk")
	 * @param structureMapUrl       the canonical URL of the StructureMap to apply
	 * @return a Binary resource containing the exported datamart
	 */
	public Binary exportDatamart(
		CanonicalType researchStudyUrl,
		Endpoint researchStudyEndpoint,
		Endpoint structureMapEndpoint,
		Endpoint terminologyEndpoint,
		Endpoint remoteEndpoint,
		String type,
		CanonicalType structureMapUrl
	) {
		Objects.requireNonNull(researchStudyUrl, "researchStudyUrl is required");
		Repository repo = Repositories.proxy(repository, false, structureMapEndpoint, researchStudyEndpoint, remoteEndpoint);
		Bundle b = repo.search(Bundle.class, ResearchStudy.class, Searches.byCanonical(researchStudyUrl.getCanonical()), null);
		if (b.getEntry().isEmpty()) {
			var errorMsg = String.format("Unable to find ResearchStudy with url: %s", researchStudyUrl.getCanonical());
			throw new ResourceNotFoundException(errorMsg);
		}
		ResearchStudy researchStudy = (ResearchStudy) b.getEntry().get(0).getResource();

		switch (ExportType.fromCode(type)) {
			case CSV:
				if (structureMapUrl == null || structureMapUrl.getValue() == null || structureMapUrl.getValue().isBlank()) {
					throw new ResourceNotFoundException("structureMapUrl is required for CSV export");
				}

				Bundle structureMaps = repo.search(Bundle.class, StructureMap.class, Searches.byCanonical(structureMapUrl.getCanonical()), null);
				if (structureMaps.getEntry().isEmpty()) {
					var errorMsg = String.format("Unable to find StructureMap with url: %s", structureMapUrl.getCanonical());
					throw new ResourceNotFoundException(errorMsg);
				}
				StructureMap structureMap = (StructureMap) structureMaps.getEntry().get(0).getResource();
				return new DatamartTransformation(repo).transform(researchStudy, structureMap);
			case REST:
				String listId = ResearchStudyUtils.getEvaluationListId(researchStudy);
				Bundle dmBundle = new DatamartTransformation(repo).fetchDataMartBundle(listId);
				TransactionBundleBuilder builder = new TransactionBundleBuilder();
				var resources = builder.extractResources(dmBundle);
				if (resources.isEmpty()) {
					throw new ResourceNotFoundException("No FHIR resources found to export in the datamart bundle for list: " + listId);
				}
				Bundle bundle = builder.buildTransaction(resources);

				if (remoteEndpoint == null || !remoteEndpoint.hasAddress()) {
					throw new InvalidRequestException("remoteEndpoint is required for REST export.");
				}

				FhirContext context = repo.fhirContext();
				IGenericClient client = context.newRestfulGenericClient(remoteEndpoint.getAddress());
				Bundle transactionResponse = client.transaction().withBundle(bundle).execute();
				String transaction = repo.fhirContext().newJsonParser().encodeResourceToString(transactionResponse);
				Binary binary = new Binary();
				binary.setContentType("application/fhir+json");
				binary.setData(transaction.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				return binary;

			case BULK:
				String evalListId = ResearchStudyUtils.getEvaluationListId(researchStudy);
				Bundle evalBundle = new DatamartTransformation(repo).fetchDataMartBundle(evalListId);

				TransactionBundleBuilder extractor = new TransactionBundleBuilder();
				Collection<Resource> res = extractor.extractResources(evalBundle);

				String remoteBaseUrl = remoteEndpoint.getAddress();


				BulkNdjsonWriter writer = new BulkNdjsonWriter(repo, remoteBaseUrl);
				BulkNdjsonWriter.Result result = writer.writeAndBuildManifest((Collection) res);

				String paramsJson = repo.fhirContext().newJsonParser().encodeResourceToString(result.manifest);
				Binary bin = new Binary();
				bin.setContentType("application/fhir+json");
				bin.setData(paramsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				return bin;
			default:
				throw new IllegalArgumentException("Unsupported export type: " + type);
		}
	}
}
