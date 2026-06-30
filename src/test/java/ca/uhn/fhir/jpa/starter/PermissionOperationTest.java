package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.fyrstain.fhir.security.core.PermissionHelper;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r5.model.Permission;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {Application.class}, properties = {
		"spring.datasource.url=jdbc:h2:mem:permissionr4",
		"spring.ai.mcp.server.enabled=false",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.permission.enabled=true",
		"hapi.fhir.fhir_version=r4"
})
class PermissionOperationTest {

	@LocalServerPort
	private int port;

	private IGenericClient client;
	private final FhirContext r4Context = FhirContext.forR4();
	private final FhirContext r5Context = FhirContext.forR5();

	@BeforeEach
	void setUp() {
		r4Context.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		r4Context.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		client = r4Context.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
	}

	@Test
	void authorizeRequest_shouldAllowAndRewriteSearchParameters() {
		Permission permission = PermissionHelper.buildPermission(true);
		Permission.RuleComponent rule = PermissionHelper.newRule(true);
		rule.addActivity(PermissionHelper.activity("search"));
		rule.addData(PermissionHelper.dataForInstance("Patient", null, "name=Toto"));
		permission.addRule(rule);

		Parameters input = new Parameters();
		input.addParameter().setName("fhirVersion").setValue(new CodeType("R4"));
		input.addParameter().setName("authorization").setValue(new StringType("Bearer test-token"));
		input.addParameter().setName("method").setValue(new CodeType("GET"));
		input.addParameter().setName("url").setValue(new StringType("/fhir/Patient"));
		input.addParameter().setName("resourceType").setValue(new CodeType("Patient"));
		input.addParameter()
				.setName("permissionJson")
				.setValue(new StringType(r5Context.newJsonParser().encodeResourceToString(permission)));

		Parameters output = client.operation()
				.onServer()
				.named("$authorize-request")
				.withParameters(input)
				.returnResourceType(Parameters.class)
				.execute();

		Assertions.assertEquals("ALLOW_WITH_REWRITE", getString(output, "decision"));
		Assertions.assertEquals("true", getString(output, "allowed"));
		Assertions.assertEquals("200", getString(output, "httpStatus"));
		Assertions.assertEquals("name=Toto", getString(output, "rewrittenQueryString"));
	}

	@Test
	void filterResponse_shouldRemoveDeniedFields() {
		Permission permission = PermissionHelper.buildPermission(true);
		Permission.RuleComponent rule = PermissionHelper.newRule(false);
		rule.addActivity(PermissionHelper.activity("read"));
		rule.addData(PermissionHelper.dataForInstance("Patient", "Patient.telecom", null));
		permission.addRule(rule);

		Patient patient = new Patient();
		patient.addName().setFamily("Doe").addGiven("John");
		patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("0102030405");

		Parameters input = new Parameters();
		input.addParameter().setName("fhirVersion").setValue(new CodeType("R4"));
		input.addParameter().setName("authorization").setValue(new StringType("Bearer test-token"));
		input.addParameter().setName("statusCode").setValue(new org.hl7.fhir.r4.model.IntegerType(200));
		input.addParameter()
				.setName("responseBodyJson")
				.setValue(new StringType(r4Context.newJsonParser().encodeResourceToString(patient)));
		input.addParameter()
				.setName("permissionJson")
				.setValue(new StringType(r5Context.newJsonParser().encodeResourceToString(permission)));

		Parameters output = client.operation()
				.onServer()
				.named("$filter-response")
				.withParameters(input)
				.returnResourceType(Parameters.class)
				.execute();

		Patient filteredPatient =
				(Patient) r4Context.newJsonParser().parseResource(getString(output, "responseBodyJson"));
		Assertions.assertFalse(filteredPatient.hasTelecom());
		Assertions.assertTrue(filteredPatient.hasName());
	}

	private String getString(Parameters parameters, String name) {
		return parameters.getParameter().stream()
				.filter(parameter -> name.equals(parameter.getName()))
				.map(Parameters.ParametersParameterComponent::getValue)
				.filter(java.util.Objects::nonNull)
				.map(value -> value.primitiveValue())
				.findFirst()
				.orElse(null);
	}
}
