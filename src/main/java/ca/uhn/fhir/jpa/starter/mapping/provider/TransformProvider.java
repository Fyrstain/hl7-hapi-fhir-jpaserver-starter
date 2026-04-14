package ca.uhn.fhir.jpa.starter.mapping.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.starter.mapping.service.FFHIRPathHostServices;
import ca.uhn.fhir.jpa.starter.mapping.service.Mapper;
import ca.uhn.fhir.jpa.starter.mapping.service.TransformerService;
import ca.uhn.fhir.jpa.starter.mapping.validation.ExtendedRemoteTerminologyServiceValidationSupport;
import ca.uhn.fhir.jpa.starter.mapping.validation.PersistedValidationSupportClass;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r4.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StructureMap;
import org.hl7.fhir.r4.model.UriType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TransformProvider {

	private static final Logger ourLogger = LoggerFactory.getLogger(TransformProvider.class);
	private final IFhirResourceDao<StructureMap> myStructureMapDao;
	private final String defaultTerminologyEndpoint;
	private final String defaultStructureMapEndpoint;
	private PersistedValidationSupportClass validationSupport;

	public TransformProvider(
			IFhirResourceDao<StructureMap> theStructureMapDao,
			String defaultTerminologyEndpoint,
			String defaultStructureMapEndpoint) {
		myStructureMapDao = theStructureMapDao;
		this.defaultTerminologyEndpoint = defaultTerminologyEndpoint;
		this.defaultStructureMapEndpoint = defaultStructureMapEndpoint;
	}

	@Operation(name = "$transform")
	public Parameters transform(@ResourceParam Parameters parameters) {

		ourLogger.info(
				"Transform operation called with {} parameters",
				parameters.getParameter().size());
		for (Parameters.ParametersParameterComponent param : parameters.getParameter()) {
			ourLogger.info(
					"Parameter: {} = {}",
					param.getName(),
					param.getValue() != null
							? param.getValue()
							: (param.getResource() != null
									? param.getResource().getClass().getSimpleName()
									: "null"));
		}

		FhirContext context = FhirContext.forR4();
		ValidationSupportChain validationSupport =
				new ValidationSupportChain(getValidationSupport(), new DefaultProfileValidationSupport(context));

		String terminologyUrl = null;
		if (parameters.getParameter("terminologyEndpoint") != null) {
			terminologyUrl =
					((Endpoint) parameters.getParameter("terminologyEndpoint").getResource()).getAddress();
		} else if (defaultTerminologyEndpoint != null && !defaultTerminologyEndpoint.isEmpty()) {
			terminologyUrl = defaultTerminologyEndpoint;
		}

		ourLogger.info("Using terminology URL: {}", terminologyUrl);

		if (terminologyUrl != null) {
			validationSupport.addValidationSupport(
					new ExtendedRemoteTerminologyServiceValidationSupport(context, terminologyUrl));
		}

		HapiWorkerContext hapiContext = new HapiWorkerContext(context, validationSupport);

		FHIRPathEngine fhirPathEngine = new FHIRPathEngine(hapiContext);
		fhirPathEngine.setHostServices(new FFHIRPathHostServices());

		IGenericClient clientStructureMap = null;
		String structureMapServer = null;
		if (parameters.getParameter("structureMapEndpoint") != null) {
			structureMapServer =
					((Endpoint) parameters.getParameter("structureMapEndpoint").getResource()).getAddress();
		} else if (defaultStructureMapEndpoint != null && !defaultStructureMapEndpoint.isEmpty()) {
			structureMapServer = defaultStructureMapEndpoint;
		}

		ourLogger.info("Using structure map server: {}", structureMapServer);

		if (structureMapServer != null) {
			clientStructureMap = context.newRestfulGenericClient(structureMapServer);
		}

		////////////////////////////////////////////////////////////////

		StructureMap structureMap = null;

		if (parameters.getParameter("structureMap") != null) {
			structureMap =
					((StructureMap) parameters.getParameter("structureMap").getResource());
			ourLogger.info("Using provided StructureMap");
		} else if (parameters.getParameter("source") != null) {
			String structureMapUrl =
					((UriType) parameters.getParameter("source").getValue()).getValue();
			ourLogger.info("Fetching StructureMap from URL: {}", structureMapUrl);
			if (clientStructureMap != null) {
				ourLogger.info("Using remote client to fetch StructureMap");
				try {
					structureMap = clientStructureMap
							.search()
							.forResource(StructureMap.class)
							.where(StructureMap.URL.matches().value(structureMapUrl))
							.returnBundle(org.hl7.fhir.r4.model.Bundle.class)
							.execute()
							.getEntry()
							.stream()
							.filter(e -> e.getResource() instanceof StructureMap)
							.map(e -> (StructureMap) e.getResource())
							.findFirst()
							.orElse(null);
					ourLogger.info("Fetched StructureMap: {}", structureMap != null ? structureMap.getId() : "null");
				} catch (Exception e) {
					ourLogger.error("Error fetching StructureMap from remote", e);
					throw new InvalidRequestException(
							"Failed to fetch StructureMap from remote server: " + structureMapUrl, e);
				}
			} else {
				ourLogger.info("Using local DAO to fetch StructureMap");
				IBundleProvider search =
						myStructureMapDao.search(new SearchParameterMap().add("url", new UriParam(structureMapUrl)));

				if (search.isEmpty()) {
					throw new InvalidRequestException(
							String.format("Did not find StructureMap with url '%s' !", structureMapUrl));
				} else if (search.size() > 1) {
					throw new InvalidRequestException(
							String.format("Found multiple StructureMap with url '%s' !", structureMapUrl));
				}

				List<IBaseResource> resources = search.getResources(0, 1);

				structureMap = (StructureMap) resources.get(0);
				ourLogger.info("Fetched StructureMap from local: {}", structureMap.getId());
			}
		} else {
			throw new InvalidRequestException("No StructureMap parameter");
		}

		Mapper mapper = new Mapper(
				hapiContext,
				fhirPathEngine,
				terminologyUrl != null ? new TransformerService(terminologyUrl) : null,
				myStructureMapDao,
				clientStructureMap);

		////////////////////////////////////////////////////////////////

		return mapper.map(structureMap, parameters);
	}

	public IValidationSupport getValidationSupport() {
		return validationSupport;
	}
}
