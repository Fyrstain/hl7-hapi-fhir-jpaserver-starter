package ca.uhn.fhir.jpa.starter.mapping.service;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.StructureMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StructureMapResolver {

	private static final int REMOTE_WILDCARD_FETCH_SIZE = 1500;
	private static final int LOCAL_WILDCARD_FETCH_SIZE = 1500;

	private final IGenericClient clientStructureMap;
	private final IFhirResourceDao<StructureMap> structureMapDao;
	private static final Logger logger = LoggerFactory.getLogger(StructureMapResolver.class);

	public StructureMapResolver(
			IGenericClient clientStructureMap,
			IFhirResourceDao<StructureMap> structureMapDao
	) {
		this.clientStructureMap = clientStructureMap;
		this.structureMapDao = structureMapDao;
	}

	/**
	 * Retrieves a StructureMap by canonical URL.
	 * Supports exact URLs and wildcard patterns using '*'.
	 *
	 * Resolution order:
	 * 1. remote endpoint if configured
	 * 2. local DAO fallback
	 */
	public List<StructureMap> fetchStructureMapByUrl(String url) {
		validateUrl(url);

		boolean wildcard = containsWildcard(url);

		List<StructureMap> structureMap = null;

		if (clientStructureMap != null) {
			try {
				if (wildcard) {
					structureMap = fetchRemoteByWildcard(url);
				} else {
					StructureMap fetched = fetchRemoteByExactUrl(url);
					if (fetched != null) {
						structureMap = List.of(fetched);
					}
				}

				if (structureMap != null) {
					logger.info("Fetched StructureMap from remote endpoint: {}", url);
					return structureMap;
				}

				logger.info("No StructureMap found remotely for URL/pattern: {} — falling back to local DAO.", url);
			} catch (Exception e) {
				logger.info("Remote StructureMap fetch failed for {}: {}", url, e.getMessage());
			}
		}

		if (structureMapDao == null) {
			throw new InvalidRequestException("No DAO available to resolve StructureMap: " + url);
		}

		if (wildcard) {
			structureMap = fetchLocalByWildcard(url);
		} else {
			StructureMap fetched = fetchLocalByExactUrl(url);
			if (fetched != null) {
				structureMap = List.of(fetched);
			}
		}

		if (structureMap == null) {
			throw new InvalidRequestException("StructureMap not found for URL/pattern: " + url);
		}

		return structureMap;
	}

	private void validateUrl(String url) {
		if (url == null || url.isBlank()) {
			throw new InvalidRequestException("StructureMap URL cannot be null or empty");
		}
	}

	public boolean containsWildcard(String url) {
		return url.indexOf('*') >= 0;
	}

	private StructureMap fetchRemoteByExactUrl(String url) {
		Bundle bundle = clientStructureMap
				.search()
				.forResource(StructureMap.class)
				.where(StructureMap.URL.matches().value(url))
				.returnBundle(Bundle.class)
				.execute();

		List<StructureMap> matches = toStructureMaps(bundle);

		if (matches.isEmpty()) {
			return null;
		}

		if (matches.size() > 1) {
			logger.info("Multiple remote StructureMaps found for {} — using the first one.", url);
		}

		return matches.get(0);
	}

	/**
	 * Wildcard resolution cannot rely on a standard FHIR URL search.
	 * We fetch a batch of StructureMaps and filter client-side.
	 */
	private List<StructureMap> fetchRemoteByWildcard(String wildcardUrl) {
		Pattern pattern = wildcardToRegex(wildcardUrl);

		Bundle bundle = clientStructureMap
				.search()
				.forResource(StructureMap.class)
				.count(REMOTE_WILDCARD_FETCH_SIZE)
				.returnBundle(Bundle.class)
				.execute();

		List<StructureMap> matches = toStructureMaps(bundle).stream()
				.filter(sm -> matchesWildcard(sm, pattern))
				.collect(Collectors.toList());

		if (matches.isEmpty()) {
			return null;
		}

		return matches;
	}

	private StructureMap fetchLocalByExactUrl(String url) {
		SearchParameterMap searchMap = new SearchParameterMap()
				.add("url", new UriParam(url));

		IBundleProvider search = structureMapDao.search(searchMap);

		if (search == null || search.size() == null || search.size() == 0) {
			return null;
		}

		if (search.size() > 1) {
			logger.info("Multiple local StructureMaps found for {} — using the first one.", url);
		}

		List<IBaseResource> resources = search.getResources(0, 1);
		if (resources == null || resources.isEmpty()) {
			return null;
		}

		return (StructureMap) resources.get(0);
	}

	/**
	 * Wildcard resolution on local DAO:
	 * fetch a batch and filter in memory.
	 */
	private List<StructureMap> fetchLocalByWildcard(String wildcardUrl) {
		Pattern pattern = wildcardToRegex(wildcardUrl);

		SearchParameterMap searchMap = new SearchParameterMap();
		searchMap.setCount(LOCAL_WILDCARD_FETCH_SIZE);

		IBundleProvider search = structureMapDao.search(searchMap);

		if (search == null || search.size() == null || search.size() == 0) {
			return null;
		}

		int size = Math.min(search.size(), LOCAL_WILDCARD_FETCH_SIZE);
		List<IBaseResource> resources = search.getResources(0, size);

		List<StructureMap> matches = resources.stream()
				.filter(StructureMap.class::isInstance)
				.map(StructureMap.class::cast)
				.filter(sm -> matchesWildcard(sm, pattern))
				.collect(Collectors.toList());

		if (matches.isEmpty()) {
			return null;
		}

		return matches;
	}

	private List<StructureMap> toStructureMaps(Bundle bundle) {
		if (bundle == null || !bundle.hasEntry()) {
			return Collections.emptyList();
		}

		return bundle.getEntry().stream()
				.map(Bundle.BundleEntryComponent::getResource)
				.filter(StructureMap.class::isInstance)
				.map(StructureMap.class::cast)
				.collect(Collectors.toList());
	}

	private boolean matchesWildcard(StructureMap structureMap, Pattern pattern) {
		return structureMap != null
				&& structureMap.hasUrl()
				&& pattern.matcher(structureMap.getUrl()).matches();
	}

	/**
	 * Converts a wildcard pattern using '*' into a safe anchored regex.
	 *
	 * Example:
	 *   http://hl7.org/fhir/StructureMap/*4to5
	 * becomes:
	 *   ^\Qhttp://hl7.org/fhir/StructureMap/\E.*\Q4to5\E$
	 */
	private Pattern wildcardToRegex(String wildcard) {
		String[] parts = wildcard.split("\\*", -1);
		StringBuilder regex = new StringBuilder("^");

		for (int i = 0; i < parts.length; i++) {
			regex.append(Pattern.quote(parts[i]));
			if (i < parts.length - 1) {
				regex.append(".*");
			}
		}

		regex.append("$");
		return Pattern.compile(regex.toString());
	}
}