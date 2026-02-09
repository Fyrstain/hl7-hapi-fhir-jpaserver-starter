package ca.uhn.fhir.jpa.starter.datamart.service.r5.service;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.Resource;

import java.util.*;

public class TransactionBundleBuilder {

	/**
	 * Extracts FHIR resources from the given datamart {@link Bundle}.
	 *
	 * @param datamartBundle input datamart bundle containing resources
	 * @return a collection of extracted {@link Resource} objects
	 * @throws InvalidRequestException if a variable or part is not a FHIR Resource
	 */
	public Collection<Resource> extractResources(Bundle datamartBundle) {
		List<Resource> resources = new ArrayList<>();
		for (Bundle.BundleEntryComponent entry : datamartBundle.getEntry()) {
			Resource resource = entry.getResource();
			if (resource == null) continue;
			if (resource instanceof Parameters parameters) {
				parameters.getParameter().forEach(param -> {

					if("Patient".equalsIgnoreCase(param.getName())){
						return;
					}
					if (param.getResource() != null) {
						resources.add(param.getResource());
					} else if (param.hasValue()) {
						throw new InvalidRequestException(
							"Variable '" + param.getName() + "' is of type '" + param.getValue().fhirType() + "' – expected a FHIR Resource."
						);

					}
					// resources in parts
					param.getPart().forEach(part -> {
						if (part.getResource() != null) {
							resources.add(part.getResource());
						} else if (part.hasValue()) {
							throw new InvalidRequestException(
								"Variable '" + part.getName() + "' is of type '" + part.getValue().fhirType() + "' – expected a FHIR Resource."
							);
						}
					});
				});
			} else if (!"List".equals(resource.fhirType()) && !"Parameters".equals(resource.fhirType())) {
				resources.add(resource);
			}
		}
		return resources;
	}

	/**
	 * Builds a transaction {@link Bundle} from the given resources.
	 *
	 * @param resources collection of resources to include in the transaction
	 * @return a transaction {@link Bundle}
	 */
	public Bundle buildTransaction(Collection<Resource> resources) {
		Bundle transaction = new Bundle();
		transaction.setType(Bundle.BundleType.TRANSACTION);
		for (Resource r : resources) {
			Bundle.BundleEntryComponent be = transaction.addEntry();
			be.setResource(r);
			be.getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl(r.fhirType());
		}
		return transaction;
	}
}
