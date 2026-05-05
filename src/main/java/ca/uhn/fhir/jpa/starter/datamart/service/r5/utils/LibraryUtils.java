package ca.uhn.fhir.jpa.starter.datamart.service.r5.utils;

import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r5.model.*;
import org.opencds.cqf.fhir.api.Repository;
import org.opencds.cqf.fhir.utility.search.Searches;

public class LibraryUtils {

	/**
	 * Resolves the Library id to use for an expression
	 *
	 * @param evidenceVariable parent EV if not present on the node
	 * @param fallback         fallback library id
	 * @return a concrete library id to call
	 */
	public static String resolveLibraryId(Repository repository,
													  EvidenceVariable evidenceVariable,
													  String fallback) {

		String canonical = readCqfLibraryCanonical(evidenceVariable);
		if (canonical == null) return fallback;

		try {
			Bundle b = repository.search(Bundle.class, Library.class, Searches.byCanonical(canonical), null);
			if (b.hasEntry() && b.getEntryFirstRep().hasResource()) {
				Resource r = b.getEntryFirstRep().getResource();
				if (r instanceof Library lib && lib.hasId()) {
					return lib.getIdElement().getIdPart();
				}
			}
		} catch (Exception exception) {
			throw new ResourceNotFoundException(String.format("Unable to find Library with url: %s", canonical));
		}

		String tail = tailId(canonical);
		return tail != null ? tail : fallback;
	}

	/**
	 * Reads the canonical value from a {@code cqf-library} extension if present.
	 *
	 * @param evidenceVariable evidence variable
	 * @return canonical URL string or null
	 */
	private static String readCqfLibraryCanonical(EvidenceVariable evidenceVariable) {
		Extension extension = evidenceVariable.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/cqf-library");
		if (extension != null) {
			return extension.getValueCanonicalType().getValue();
		}
		return null;
	}

	/**
	 * Extracts the terminal id from a canonical URL, ignoring any version suffix.
	 *
	 * @param canonical canonical URL with optional
	 * @return id tail or null
	 */
	private static String tailId(String canonical) {
		if (canonical == null) return null;
		// Remove version
		String noVer = canonical.split("\\|")[0];
		int slash = noVer.lastIndexOf('/');
		return (slash >= 0) ? noVer.substring(slash + 1) : noVer;
	}

}
