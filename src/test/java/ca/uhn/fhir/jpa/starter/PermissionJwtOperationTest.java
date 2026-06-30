package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import com.fyrstain.fhir.security.core.PermissionHelper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r5.model.Permission;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {Application.class}, properties = {
		"spring.datasource.url=jdbc:h2:mem:permissionjwt",
		"spring.ai.mcp.server.enabled=false",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.permission.enabled=true",
		"hapi.fhir.permission.require-token=true",
		"hapi.fhir.permission.token-validation.enabled=true",
		"hapi.fhir.permission.token-validation.mode=hmac",
		"hapi.fhir.permission.token-validation.shared-secret=permission-secret-1234567890-abcdef",
		"hapi.fhir.permission.token-validation.issuer=permission-test",
		"hapi.fhir.permission.token-validation.audience=permission-engine",
		"hapi.fhir.permission.token-source.enabled=true",
		"hapi.fhir.permission.token-source.claim-names=permissions",
		"hapi.fhir.fhir_version=r4"
})
class PermissionJwtOperationTest {

	private static final String SECRET = "permission-secret-1234567890-abcdef";

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
	void authorizeRequest_shouldLoadPermissionsFromValidatedToken() throws Exception {
		Permission permission = PermissionHelper.buildPermission(true);
		Permission.RuleComponent rule = PermissionHelper.newRule(true);
		rule.addActivity(PermissionHelper.activity("search"));
		rule.addData(PermissionHelper.dataForInstance("Patient", null, "name=Toto"));
		permission.addRule(rule);

		String permissionJson = r5Context.newJsonParser().encodeResourceToString(permission);
		String token = buildToken(SECRET, List.of(permissionJson));

		Parameters input = new Parameters();
		input.addParameter().setName("fhirVersion").setValue(new CodeType("R4"));
		input.addParameter().setName("authorization").setValue(new StringType("Bearer " + token));
		input.addParameter().setName("method").setValue(new CodeType("GET"));
		input.addParameter().setName("url").setValue(new StringType("/fhir/Patient"));
		input.addParameter().setName("resourceType").setValue(new CodeType("Patient"));

		Parameters output = client.operation()
				.onServer()
				.named("$authorize-request")
				.withParameters(input)
				.returnResourceType(Parameters.class)
				.execute();

		Assertions.assertEquals("ALLOW_WITH_REWRITE", getString(output, "decision"));
		Assertions.assertEquals("200", getString(output, "httpStatus"));
		Assertions.assertEquals("name=Toto", getString(output, "rewrittenQueryString"));
	}

	@Test
	void authorizeRequest_shouldReturn401WhenTokenIsInvalid() throws Exception {
		String invalidToken = buildToken("wrong-secret-1234567890-abcdef-xyz123", List.of());

		Parameters input = new Parameters();
		input.addParameter().setName("fhirVersion").setValue(new CodeType("R4"));
		input.addParameter().setName("authorization").setValue(new StringType("Bearer " + invalidToken));
		input.addParameter().setName("method").setValue(new CodeType("GET"));
		input.addParameter().setName("url").setValue(new StringType("/fhir/Patient"));
		input.addParameter().setName("resourceType").setValue(new CodeType("Patient"));

		BaseServerResponseException error = Assertions.assertThrows(
				BaseServerResponseException.class,
				() -> client.operation()
						.onServer()
						.named("$authorize-request")
						.withParameters(input)
						.returnResourceType(Parameters.class)
						.execute());

		Assertions.assertEquals(401, error.getStatusCode());
	}

	private String buildToken(String secret, List<String> permissionJsons) throws JOSEException {
		JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
				.subject("permission-user")
				.issuer("permission-test")
				.audience("permission-engine")
				.claim("preferred_username", "permission-user")
				.claim("permissions", permissionJsons)
				.issueTime(new Date())
				.expirationTime(Date.from(Instant.now().plusSeconds(300)))
				.build();

		SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
		signedJWT.sign(new MACSigner(secret));
		return signedJWT.serialize();
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
