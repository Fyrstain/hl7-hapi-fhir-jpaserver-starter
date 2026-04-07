package ca.uhn.fhir.jpa.starter.mapping.service;

import ca.uhn.fhir.jpa.starter.mapping.model.exception.MatchboxTransformException;
import ch.ahdis.matchbox.engine.MatchboxEngine;
import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.StructureMap;
import org.hl7.fhir.r4.model.MetadataResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

//TODO
//@Service
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
	 * @param sourceJson   source to transform
	 * @return transformed resource
	 */
	public String transform(StructureMap structureMap, String sourceJson) {
		return transform(structureMap, null, null, sourceJson);
	}

    /**
     * Transform a resource using a StructureMap.
     *
     * @param structureMap StructureMap for the transform
	  * @param dependencies dependencies for the StructureMap
	  * @param customModel  custom model definition
     * @param sourceJson   source to transform
     * @return transformed resource
     */
    public String transform(StructureMap structureMap, List<StructureMap> dependencies, List<StructureDefinition> customModel, String sourceJson) {
        Objects.requireNonNull(structureMap, "structureMap must not be null");
        Objects.requireNonNull(sourceJson, "sourceJson must not be null");

        if (!structureMap.hasUrl() || structureMap.getUrl().isBlank()) {
            throw new IllegalArgumentException("StructureMap.url est obligatoire pour exécuter le transform");
        }

        synchronized (engine) {
			  if (dependencies != null) {
				  for (StructureMap dependency : dependencies) {
					  registerCanonicalResource(dependency);
				  }
			  }

			  if (customModel != null) {
				  for (StructureDefinition model : customModel) {
					  registerCanonicalResource(model);
				  }
			  }

            registerCanonicalResource(structureMap);

            try {
					//TODO Voir pour le parser ici ? (à retirer pour supporter les custom formats ?)
                return engine.transform(
                    sourceJson,
                    true,
                    structureMap.getUrl(),
						 false,
						 null
                );
            } catch (Exception e) {
                throw new MatchboxTransformException(
                    "Erreur lors de la transformation avec le StructureMap " + structureMap.getUrl(),
                    e
                );
            }
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
                registerCanonicalResource(structureMap);
                return structureMap;
            } catch (Exception e) {
                throw new MatchboxTransformException("Erreur lors du parsing du FML", e);
            }
        }
    }

    private void registerCanonicalResource(MetadataResource resource) {
        try {
            engine.addCanonicalResource(resource);
        } catch (Exception e) {
            throw new MatchboxTransformException(
                "Impossible d’enregistrer le StructureMap dans le MatchboxEngine: " + resource.getUrl(),
                e
            );
        }
    }
}