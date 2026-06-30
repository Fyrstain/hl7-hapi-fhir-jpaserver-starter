package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.fyrstain.fhir.security.core.PermissionHelper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Permission;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {Application.class}, properties = {
		"spring.datasource.url=jdbc:h2:mem:permissionrepo",
		"spring.ai.mcp.server.enabled=false",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.permission.enabled=true",
		"hapi.fhir.permission.token-source.enabled=false",
		"hapi.fhir.permission.repository.enabled=true",
		"hapi.fhir.permission.repository.query-template=Permission?status=active&user=${userId}",
		"hapi.fhir.permission.repository.timeout-seconds=10",
		"hapi.fhir.fhir_version=r4"
})
class PermissionRepositoryOperationTest {

	private static final FhirContext R5_CONTEXT = FhirContext.forR5();
	private static HttpServer ourRepositoryServer;
	private static String ourRepositoryBaseUrl;

	@LocalServerPort
	private int port;

	private IGenericClient client;
	private final FhirContext r4Context = FhirContext.forR4();

	@AfterAll
	static void afterAll() {
		if (ourRepositoryServer != null) {
			ourRepositoryServer.stop(0);
		}
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) throws IOException {
		ensureRepositoryServerStarted();
		registry.add("hapi.fhir.permission.repository.base-url", () -> ourRepositoryBaseUrl);
	}

	@BeforeEach
	void setUp() {
		r4Context.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		r4Context.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		client = r4Context.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
	}

	@Test
	void authorizeRequest_shouldLoadPermissionsFromConfiguredRepository() {
		Parameters input = new Parameters();
		input.addParameter().setName("fhirVersion").setValue(new CodeType("R4"));
		input.addParameter().setName("userId").setValue(new StringType("repo-user"));
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
		Assertions.assertEquals("name=Toto", getString(output, "rewrittenQueryString"));
	}

	private static void ensureRepositoryServerStarted() throws IOException {
		if (ourRepositoryServer != null) {
			return;
		}
		ourRepositoryServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		ourRepositoryServer.createContext("/fhir/Permission", PermissionRepositoryOperationTest::handlePermissionSearch);
		ourRepositoryServer.setExecutor(Executors.newSingleThreadExecutor());
		ourRepositoryServer.start();
		ourRepositoryBaseUrl = "http://127.0.0.1:" + ourRepositoryServer.getAddress().getPort() + "/fhir";
	}

	private static void handlePermissionSearch(HttpExchange exchange) throws IOException {
		String body = buildRepositoryResponse(exchange.getRequestURI().getQuery());
		byte[] payload = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/fhir+json");
		exchange.sendResponseHeaders(200, payload.length);
		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(payload);
		}
	}

	private static String buildRepositoryResponse(String query) {
		Permission permission = PermissionHelper.buildPermission(true);
		if (query != null && query.contains("user=repo-user")) {
			Permission.RuleComponent rule = PermissionHelper.newRule(true);
			rule.addActivity(PermissionHelper.activity("search"));
			rule.addData(PermissionHelper.dataForInstance("Patient", null, "name=Toto"));
			permission.addRule(rule);
		}

		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		if (permission.hasRule()) {
			bundle.addEntry().setResource(permission);
		}
		return R5_CONTEXT.newJsonParser().encodeResourceToString(bundle);
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
