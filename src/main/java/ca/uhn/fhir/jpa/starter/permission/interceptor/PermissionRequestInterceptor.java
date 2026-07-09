package ca.uhn.fhir.jpa.starter.permission.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.starter.permission.PermissionContextFactory;
import ca.uhn.fhir.jpa.starter.permission.PermissionProperties;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.fyrstain.fhir.security.core.FhirAuthorizationEngine;
import com.fyrstain.fhir.security.core.PermissionService;
import com.fyrstain.fhir.security.core.model.FhirRequest;
import com.fyrstain.fhir.security.core.model.PermissionContext;
import com.fyrstain.fhir.security.core.model.RequestEvaluationResult;
import com.fyrstain.fhir.security.core.r4.SimpleR4PermissionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Interceptor
public class PermissionRequestInterceptor {

	private static final Logger ourLog = LoggerFactory.getLogger(PermissionRequestInterceptor.class);

	private final PermissionService myPermissionService;
	private final PermissionProperties myPermissionProperties;
	private final PermissionContextFactory myPermissionContextFactory;

	public PermissionRequestInterceptor(
			PermissionService thePermissionService,
			PermissionProperties thePermissionProperties,
			PermissionContextFactory thePermissionContextFactory) {
		myPermissionService = thePermissionService;
		myPermissionProperties = thePermissionProperties;
		myPermissionContextFactory = thePermissionContextFactory;
	}

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	public boolean postProcessRequest(
			RequestDetails theRequestDetails,
			ServletRequestDetails theServletRequestDetails,
			HttpServletRequest theServletRequest,
			HttpServletResponse theServletResponse) {
		PermissionContext context = myPermissionContextFactory.fromRequestDetails(theRequestDetails, theServletRequestDetails);

		FhirRequest request = buildRequest(theRequestDetails);
		RequestEvaluationResult result = authorizationEngine().evaluateRequest(context, request);

		if (!result.isAllowed()) {
			ourLog.debug("Permission request interceptor denied {} {}", request.getMethod(), request.getResourceType());
			if (myPermissionProperties.isFailClosed()) {
				throw new ForbiddenOperationException("Request is not authorized");
			}
			return true;
		}

		Map<String, String[]> updatedParameters = toServletParameters(result.getModifiedSearchParameters());
		if (!updatedParameters.isEmpty()) {
			theRequestDetails.setParameters(updatedParameters);
			theRequestDetails.setCompleteUrl(buildCompleteUrl(theRequestDetails, updatedParameters));
		}
		return true;
	}

	private FhirAuthorizationEngine authorizationEngine() {
		return new FhirAuthorizationEngine(myPermissionService, new SimpleR4PermissionEvaluator());
	}

	private FhirRequest buildRequest(RequestDetails theRequestDetails) {
		String resourceType = theRequestDetails.getResourceName();
		String resourceId = theRequestDetails.getId() != null ? theRequestDetails.getId().getIdPart() : null;
		String operationName = theRequestDetails.getOperation();

		return new FhirRequest()
				.setMethod(FhirRequest.HTTPVerb.valueOf(theRequestDetails.getRequestType().name().toUpperCase(Locale.ROOT)))
				.setResourceType(resourceType)
				.setResourceId(resourceId)
				.setOperationName(operationName)
				.setSearchParameters(fromServletParameters(theRequestDetails.getParameters()));
	}

	private Map<String, List<String>> fromServletParameters(Map<String, String[]> theParameters) {
		Map<String, List<String>> searchParameters = new LinkedHashMap<>();
		if (theParameters == null) {
			return searchParameters;
		}
		for (Map.Entry<String, String[]> entry : theParameters.entrySet()) {
			List<String> values = new ArrayList<>();
			if (entry.getValue() != null) {
				for (String raw : entry.getValue()) {
					if (raw == null) {
						continue;
					}
					for (String split : raw.split(",")) {
						values.add(split);
					}
				}
			}
			searchParameters.put(entry.getKey(), values);
		}
		return searchParameters;
	}

	private Map<String, String[]> toServletParameters(Map<String, List<String>> theParameters) {
		Map<String, String[]> servletParameters = new LinkedHashMap<>();
		if (theParameters == null) {
			return servletParameters;
		}
		for (Map.Entry<String, List<String>> entry : theParameters.entrySet()) {
			servletParameters.put(entry.getKey(), entry.getValue().toArray(String[]::new));
		}
		return servletParameters;
	}

	private String buildCompleteUrl(RequestDetails theRequestDetails, Map<String, String[]> theParameters) {
		String requestPath = theRequestDetails.getRequestPath();
		if (requestPath == null) {
			return theRequestDetails.getCompleteUrl();
		}

		StringBuilder builder = new StringBuilder(requestPath);
		if (!theParameters.isEmpty()) {
			builder.append('?');
			boolean first = true;
			for (Map.Entry<String, String[]> entry : theParameters.entrySet()) {
				if (entry.getValue() == null || entry.getValue().length == 0) {
					continue;
				}
				for (String value : entry.getValue()) {
					if (!first) {
						builder.append('&');
					}
					builder.append(entry.getKey()).append('=').append(value);
					first = false;
				}
			}
		}
		return builder.toString();
	}
}
