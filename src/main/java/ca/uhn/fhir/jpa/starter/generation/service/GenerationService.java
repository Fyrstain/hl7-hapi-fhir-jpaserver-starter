package ca.uhn.fhir.jpa.starter.generation.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.generation.provider.GenerationProperties;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class GenerationService {

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();

	private final GenerationProperties properties;

	public GenerationService(GenerationProperties properties) {
		this.properties = properties;
	}

	public Bundle generate(String realm, Integer count) {
		validateRequest(realm, count);

		Path jobDirectory = properties.getWorkDir().resolve(UUID.randomUUID().toString());
		Path outputDirectory = jobDirectory.resolve("output");

		try {
			Files.createDirectories(outputDirectory);

			String inputFolder = resolveInputFolder(realm);

			ProcessBuilder processBuilder = new ProcessBuilder(
				"java",
				"-jar",
				properties.getJarPath().toString(),
				"-c",
				"-i",
				inputFolder,
				"-mvn",
				properties.getMavenPath(),
				"-o",
				outputDirectory.toString(),
				"-n",
				String.valueOf(count)
			);

			processBuilder.redirectErrorStream(true);

			Process process = processBuilder.start();

			StringBuilder logs = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream())
			)) {
				String line;
				while ((line = reader.readLine()) != null) {
					logs.append(line).append(System.lineSeparator());
				}
			}

			int exitCode = process.waitFor();

			if (exitCode != 0) {
				throw new IllegalStateException(
					"Dolus generation failed with exit code " + exitCode + "\n" + logs
				);
			}

			Path ndjson = outputDirectory.resolve("generated.ndjson");

			if (!Files.exists(ndjson)) {
				throw new IllegalStateException("Dolus did not produce generated.ndjson");
			}

			return readNdjsonAsBundle(ndjson);

		} catch (Exception e) {
			throw new IllegalStateException("Unable to generate resources with Dolus with message : " + e.getMessage(), e);
		}
	}

	private void validateRequest(String realm, Integer count) {
		if (realm == null || realm.isBlank()) {
			throw new IllegalArgumentException("realm is required");
		}

		if (count == null || count < 1) {
			throw new IllegalArgumentException("count must be greater than or equal to 1");
		}

		if (count > 100) {
			throw new IllegalArgumentException("count must be lower than or equal to 100 for the initial POC");
		}
	}

	private String resolveInputFolder(String realm) {
		try {
			Path realmPath = properties.getInputBaseDir().resolve(realm);
			if (Files.exists(realmPath)) {
				return realmPath.toString();
			}
			throw new IllegalArgumentException(
				"Unsupported realm : " + realm
			);
		} catch (Exception e) {
			throw new IllegalArgumentException(
				"Unsupported realm : " + realm
			);
		}
	}

	private Bundle readNdjsonAsBundle(Path ndjson) throws Exception {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.COLLECTION);

		for (String line : Files.readAllLines(ndjson)) {
			if (line == null || line.isBlank()) {
				continue;
			}

			Resource resource = (Resource) FHIR_CONTEXT
				.newJsonParser()
				.parseResource(line);

			bundle.addEntry().setResource(resource);
		}

		return bundle;
	}
}