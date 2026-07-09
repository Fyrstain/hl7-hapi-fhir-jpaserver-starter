package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.AdditionalRequestHeadersInterceptor;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import com.fyrstain.fhir.security.core.PermissionHelper;
import com.fyrstain.fhir.security.core.PermissionService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r5.model.Permission;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@ActiveProfiles("test")
@Import(PermissionInterceptorTest.PermissionInterceptorTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {Application.class}, properties = {
		"spring.datasource.url=jdbc:h2:mem:permissioninterceptors",
		"spring.ai.mcp.server.enabled=false",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.permission.enabled=true",
		"hapi.fhir.permission.enable-interceptors=true",
		"hapi.fhir.fhir_version=r4"
})
class PermissionInterceptorTest {

	@LocalServerPort
	private int port;

	private final FhirContext r4Context = FhirContext.forR4();
	private IGenericClient authorizedClient;
	private IGenericClient unauthorizedClient;

	@BeforeEach
	void setUp() {
		r4Context.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		r4Context.getRestfulClientFactory().setSocketTimeout(1200 * 1000);

		String serverBase = "http://localhost:" + port + "/fhir/";
		authorizedClient = r4Context.newRestfulGenericClient(serverBase);
		unauthorizedClient = r4Context.newRestfulGenericClient(serverBase);

		AdditionalRequestHeadersInterceptor headers = new AdditionalRequestHeadersInterceptor();
		headers.addHeaderValue("X-User-Id", "permission-user");
		headers.addHeaderValue("X-User-Name", "Permission Tester");
		authorizedClient.registerInterceptor(headers);
	}

	@Test
	void interceptors_shouldRewriteSearchAndFilterResponses() {
		String totoId = authorizedClient.create()
				.resource(newPatient("Toto", "Allowed", "0102030405"))
				.execute()
				.getId()
				.getIdPart();

		authorizedClient.create()
				.resource(newPatient("Bob", "Blocked", "0607080910"))
				.execute();

		Bundle searchResult = authorizedClient.search()
				.forResource(Patient.class)
				.returnBundle(Bundle.class)
				.cacheControl(new CacheControlDirective().setNoCache(true))
				.execute();

		Assertions.assertEquals(1, searchResult.getEntry().size());
		Patient searchedPatient = (Patient) searchResult.getEntryFirstRep().getResource();
		Assertions.assertEquals("Allowed", searchedPatient.getNameFirstRep().getFamily());
		Assertions.assertEquals("Toto", searchedPatient.getNameFirstRep().getGivenAsSingleString());
		Assertions.assertFalse(searchedPatient.hasTelecom());

		Patient readPatient = authorizedClient.read().resource(Patient.class).withId(totoId).execute();
		Assertions.assertFalse(readPatient.hasTelecom());
		Assertions.assertEquals("Allowed", readPatient.getNameFirstRep().getFamily());
	}

	@Test
	void interceptors_shouldRejectRequestsWithoutResolvedPermissions() {
		String patientId = authorizedClient.create()
				.resource(newPatient("Hidden", "Patient", "0123456789"))
				.execute()
				.getId()
				.getIdPart();

		BaseServerResponseException error = Assertions.assertThrows(
				BaseServerResponseException.class,
				() -> unauthorizedClient.read().resource(Patient.class).withId(patientId).execute());

		Assertions.assertEquals(403, error.getStatusCode());
	}

	private Patient newPatient(String given, String family, String phone) {
		Patient patient = new Patient();
		patient.addName(new HumanName().setFamily(family).addGiven(given));
		patient.addTelecom()
				.setSystem(ContactPoint.ContactPointSystem.PHONE)
				.setValue(phone);
		return patient;
	}

	@TestConfiguration
	static class PermissionInterceptorTestConfig {

		@Bean
		PermissionService permissionService() {
			Permission permission = PermissionHelper.buildPermission(true);

			Permission.RuleComponent allowPatientAccess = PermissionHelper.newRule(true);
			allowPatientAccess.addActivity(PermissionHelper.activity("create", "read", "search"));
			allowPatientAccess.addData(PermissionHelper.dataForInstance("Patient", null, null));
			permission.addRule(allowPatientAccess);

			Permission.RuleComponent restrictSearchScope = PermissionHelper.newRule(true);
			restrictSearchScope.addActivity(PermissionHelper.activity("search"));
			restrictSearchScope.addData(PermissionHelper.dataForInstance("Patient", null, "name=Toto"));
			permission.addRule(restrictSearchScope);

			Permission.RuleComponent filterTelecom = PermissionHelper.newRule(false);
			filterTelecom.addData(PermissionHelper.dataForInstance("Patient", "Patient.telecom", null));
			permission.addRule(filterTelecom);

			List<IBaseResource> permissions = List.of(permission);
			return context -> "permission-user".equals(context.getUserId()) ? permissions : List.of();
		}
	}
}
