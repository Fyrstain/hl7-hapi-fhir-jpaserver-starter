package ca.uhn.fhir.jpa.starter.mapping.service;

import ca.uhn.fhir.jpa.starter.mapping.model.exception.MatchboxTransformException;
import ch.ahdis.matchbox.engine.CdaMappingEngine;
import ch.ahdis.matchbox.engine.MatchboxEngine;
import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.StructureMap;
import org.hl7.fhir.r4.model.MetadataResource;

import java.util.List;
import java.util.Objects;

public class MatchboxTransformService {

	private final FhirContext fhirContext;
	private final MatchboxEngine engine;

	public MatchboxTransformService(FhirContext fhirContext, MatchboxEngine engine) {
		this.fhirContext = fhirContext;
		this.engine = engine;
	}

	/**
	 * Transform a resource using a StructureMap.
	 *
	 * @param structureMap StructureMap for the transform
	 * @param sourceString   source to transform
	 * @return transformed resource
	 */
	public String transform(StructureMap structureMap, String sourceString) {
		return transform(structureMap, null, null, sourceString, true);
	}

	/**
	 * Transform a resource using a StructureMap.
	 *
	 * @param structureMap 	StructureMap for the transform
	 * @param dependencies 	dependencies for the StructureMap
	 * @param customModel  	custom model definition
	 * @param sourceString  source to transform
	 * @param outputJson   	if true, will return JSON, XML otherwise
	 * @return transformed resource
	 */
	public String transform(StructureMap structureMap, List<StructureMap> dependencies,
									List<StructureDefinition> customModel, String sourceString, boolean outputJson) {
		Objects.requireNonNull(structureMap, "structureMap must not be null");
		Objects.requireNonNull(sourceString, "sourceString must not be null");

		if (!structureMap.hasUrl() || structureMap.getUrl().isBlank()) {
			throw new IllegalArgumentException("StructureMap.url is mandatory for $transform");
		}

		try {
			CdaMappingEngine requestEngine = new CdaMappingEngine(engine);

			if (dependencies != null) {
				for (StructureMap dependency : dependencies) {
					registerCanonicalResource(requestEngine, dependency);
				}
			}

			if (customModel != null) {
				for (StructureDefinition model : customModel) {
					registerCanonicalResource(requestEngine, model);
				}
			}

			registerCanonicalResource(requestEngine, structureMap);

			return requestEngine.transform(
				sourceString,
				!sourceString.startsWith("<"),
				structureMap.getUrl(),
				outputJson,
				null
			);
		} catch (Exception e) {
			throw new MatchboxTransformException(
				"Error during transform with StructureMap " + structureMap.getUrl(),
				e
			);
		}
	}

	/**
	 * Optionnel : si tu reçois parfois du FML texte au lieu d’un StructureMap déjà parsé.
	 */
	public StructureMap parseAndRegisterMap(String fmlContent) {
		Objects.requireNonNull(fmlContent, "fmlContent must not be null");

		synchronized (engine) {
			try {
				StructureMap structureMap = engine.parseMap(fmlContent);
				registerCanonicalResource(engine, structureMap);
				return structureMap;
			} catch (Exception e) {
				throw new MatchboxTransformException("Error while parsing FML", e);
			}
		}
	}

	/**
	 * Register a CanonicalResource in a Matchbox engine.
	 *
	 * @param engine		the engine.
	 * @param resource	the resource.
	 */
	private void registerCanonicalResource(MatchboxEngine engine, MetadataResource resource) {
		try {
			engine.addCanonicalResource(resource);
		} catch (Exception e) {
			throw new MatchboxTransformException(
				"Error saving resource in MatchboxEngine: " + resource.getUrl(),
				e
			);
		}
	}
}