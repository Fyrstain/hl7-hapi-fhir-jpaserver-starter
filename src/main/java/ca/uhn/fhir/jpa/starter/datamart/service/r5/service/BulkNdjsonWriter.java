package ca.uhn.fhir.jpa.starter.datamart.service.r5.service;

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.Binary;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.UrlType;
import org.opencds.cqf.fhir.api.Repository;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class BulkNdjsonWriter {

	private final Repository repository;
	private final String serverBaseUrl;

	public BulkNdjsonWriter(Repository repository, String serverBaseUrl) {
		this.repository = Objects.requireNonNull(repository, "repository is required");
		this.serverBaseUrl = Objects.requireNonNull(serverBaseUrl, "serverBaseUrl is required").replaceAll("/+$", "");
	}

	/**
	 * Writes the provided resources into NDJSON files, stores them as {@link Binary} resources,
	 * and builds a manifest listing all file URLs.
	 *
	 * @param resources Collection of FHIR resources to export
	 * @return a {@link Result} containing the download URLs and the manifest
	 * @throws IllegalArgumentException if resources are null or empty
	 * @throws IllegalStateException    if a Binary resource cannot be created or has no ID
	 */
	public Result writeAndBuildManifest(Collection<IBaseResource> resources) {
		if (resources == null || resources.isEmpty()) {
			throw new IllegalArgumentException("No resources to export for bulk NDJSON.");
		}

		Map<String, List<IBaseResource>> byType = resources.stream()
			.collect(Collectors.groupingBy(IBaseResource::fhirType, LinkedHashMap::new, Collectors.toList()));

		var parser = repository.fhirContext().newJsonParser().setPrettyPrint(false);
		Map<String, String> urlsByType = new LinkedHashMap<>();

		for (Map.Entry<String, List<IBaseResource>> entry : byType.entrySet()) {
			String resourceType = entry.getKey();

			StringBuilder ndjson = new StringBuilder(entry.getValue().size() * 128);
			for (IBaseResource resource : entry.getValue()) {
				ndjson.append(parser.encodeResourceToString(resource)).append('\n');
			}
			byte[] bytes = ndjson.toString().getBytes(StandardCharsets.UTF_8);

			Binary binary = new Binary();
			binary.setContentType("application/fhir+ndjson");
			binary.setData(bytes);

			MethodOutcome outcome = repository.create(binary, null);
			String idPart = outcome.getId() != null ? outcome.getId().getIdPart() : null;
			if (idPart == null || idPart.isBlank()) {
				Binary created = (Binary) outcome.getResource();
				if (created != null && created.getIdElement() != null) {
					idPart = created.getIdElement().getIdPart();
				}
			}
			if (idPart == null || idPart.isBlank()) {
				throw new IllegalStateException("Failed to create Binary for type " + resourceType + ": no id returned.");
			}

			String downloadUrl = serverBaseUrl + "/Binary/" + idPart;
			urlsByType.put(resourceType, downloadUrl);
		}

		Parameters manifest = buildParametersManifest(urlsByType);

		return new Result(urlsByType, manifest);
	}

	/**
	 * Builds the Bulk Data Import manifest as a FHIR {@link Parameters} resource.
	 *
	 * @param urlsByType Map of resource type → Binary download URL
	 * @return {@link Parameters} manifest ready for bulk import
	 */
	private Parameters buildParametersManifest(Map<String, String> urlsByType) {
		Parameters parameters = new Parameters();

		parameters.addParameter().setName("inputFormat").setValue(new CodeType("application/fhir+ndjson"));

		Parameters.ParametersParameterComponent storage = parameters.addParameter().setName("storageDetail");
		storage.addPart().setName("type").setValue(new CodeType("https"));

		for (Map.Entry<String, String> e : urlsByType.entrySet()) {
			String type = e.getKey();
			String url = e.getValue();

			Parameters.ParametersParameterComponent input = parameters.addParameter().setName("input");
			input.addPart().setName("type").setValue(new CodeType(type));
			input.addPart().setName("url").setValue(new UrlType(url));
		}
		return parameters;
	}

	/**
	 * Result wrapper containing both the generated Binary URLs and the manifest.
	 */
	public static class Result {
		public final Map<String, String> urlsByType;
		public final Parameters manifest;

		public Result(Map<String, String> urlsByType, Parameters manifest) {
			this.urlsByType = urlsByType;
			this.manifest = manifest;
		}
	}
}
