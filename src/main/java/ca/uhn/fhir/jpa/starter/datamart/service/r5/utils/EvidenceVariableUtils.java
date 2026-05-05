package ca.uhn.fhir.jpa.starter.datamart.service.r5.utils;

import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.EvidenceVariable;
import org.opencds.cqf.fhir.api.Repository;

public class EvidenceVariableUtils {

	/**
	 * Resolves an EvidenceVariable from a canonical URL using the repository.
	 *
	 * @param canonical The canonical URL.
	 * @return The resolved EvidenceVariable, or {@code null} if not found.
	 */
	public static EvidenceVariable resolveEvidenceVariable(Repository repository, String canonical) {
		try {
			Bundle bundle = repository.search(Bundle.class, EvidenceVariable.class, org.opencds.cqf.fhir.utility.search.Searches.byCanonical(canonical), null);
			return (EvidenceVariable) bundle.getEntryFirstRep().getResource();
		} catch (Exception exception) {
			throw new ResourceNotFoundException(String.format("Unable to find EvidenceVariable with url: %s", canonical));
		}
	}
}
