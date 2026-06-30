package ca.uhn.fhir.jpa.starter.permission.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.starter.permission.PermissionContextFactory;
import ca.uhn.fhir.jpa.starter.permission.PermissionProperties;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.fyrstain.fhir.security.core.FhirAuthorizationEngine;
import com.fyrstain.fhir.security.core.PermissionService;
import com.fyrstain.fhir.security.core.model.FhirResponse;
import com.fyrstain.fhir.security.core.model.PermissionContext;
import com.fyrstain.fhir.security.core.r4.SimpleR4PermissionEvaluator;
import org.hl7.fhir.instance.model.api.IBaseResource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Interceptor
public class PermissionResponseInterceptor {

	private final PermissionService myPermissionService;
	private final PermissionContextFactory myPermissionContextFactory;

	public PermissionResponseInterceptor(
			PermissionService thePermissionService,
			PermissionProperties thePermissionProperties,
			PermissionContextFactory thePermissionContextFactory) {
		myPermissionService = thePermissionService;
		myPermissionContextFactory = thePermissionContextFactory;
	}

	@Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
	public boolean filterResponse(
			RequestDetails theRequestDetails,
			ServletRequestDetails theServletRequestDetails,
			IBaseResource theResource,
			ResponseDetails theResponseDetails,
			HttpServletRequest theServletRequest,
			HttpServletResponse theServletResponse) {
		if (theResource == null) {
			return true;
		}

		PermissionContext context = myPermissionContextFactory.fromRequestDetails(theRequestDetails, theServletRequestDetails);
		authorizationEngine().filterResponse(context, new FhirResponse().setStatusCode(200).setResource(theResource));
		return true;
	}

	private FhirAuthorizationEngine authorizationEngine() {
		return new FhirAuthorizationEngine(myPermissionService, new SimpleR4PermissionEvaluator());
	}
}
