package ca.uhn.fhir.jpa.starter.permission;

import ca.uhn.fhir.jpa.starter.annotations.OnR4Condition;
import ca.uhn.fhir.jpa.starter.permission.interceptor.PermissionRequestInterceptor;
import ca.uhn.fhir.jpa.starter.permission.interceptor.PermissionResponseInterceptor;
import ca.uhn.fhir.jpa.starter.permission.provider.PermissionOperationProvider;
import ca.uhn.fhir.jpa.starter.permission.service.CompositePermissionService;
import ca.uhn.fhir.jpa.starter.permission.service.FhirRepositoryPermissionSource;
import ca.uhn.fhir.jpa.starter.permission.service.PermissionSource;
import ca.uhn.fhir.jpa.starter.permission.service.TokenClaimPermissionSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyrstain.fhir.security.core.PermissionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Conditional(PermissionConfigCondition.class)
@EnableConfigurationProperties(PermissionProperties.class)
public class PermissionConfig {

	@Bean
	@ConditionalOnMissingBean(PermissionTokenValidator.class)
	public PermissionTokenValidator permissionTokenValidator(PermissionProperties permissionProperties) {
		return permissionProperties.getTokenValidation().isEnabled()
				? new JwtPermissionTokenValidator(permissionProperties)
				: new NoopPermissionTokenValidator();
	}

	@Bean
	public PermissionContextFactory permissionContextFactory(
			PermissionProperties permissionProperties, PermissionTokenValidator permissionTokenValidator) {
		return new PermissionContextFactory(permissionProperties, permissionTokenValidator);
	}

	@Bean
	@ConditionalOnProperty(prefix = "hapi.fhir.permission.token-source", name = "enabled", havingValue = "true", matchIfMissing = true)
	public PermissionSource tokenClaimPermissionSource(
			PermissionProperties permissionProperties, ObjectMapper objectMapper) {
		return new TokenClaimPermissionSource(permissionProperties, objectMapper);
	}

	@Bean
	@ConditionalOnProperty(prefix = "hapi.fhir.permission.repository", name = "enabled", havingValue = "true")
	public PermissionSource fhirRepositoryPermissionSource(PermissionProperties permissionProperties) {
		return new FhirRepositoryPermissionSource(permissionProperties.getRepository());
	}

	@Bean
	@ConditionalOnMissingBean(PermissionService.class)
	public PermissionService permissionService(ObjectProvider<PermissionSource> permissionSources) {
		List<PermissionSource> sources = permissionSources.orderedStream().toList();
		return new CompositePermissionService(sources);
	}

	@Bean
	@Conditional(OnR4Condition.class)
	@ConditionalOnProperty(
			prefix = "hapi.fhir.permission",
			name = "expose-operations",
			havingValue = "true",
			matchIfMissing = true)
	public PermissionOperationProvider permissionOperationProvider(
			PermissionService permissionService,
			PermissionProperties permissionProperties,
			PermissionContextFactory permissionContextFactory) {
		return new PermissionOperationProvider(permissionService, permissionProperties, permissionContextFactory);
	}

	@Bean
	@Conditional(OnR4Condition.class)
	@ConditionalOnProperty(prefix = "hapi.fhir.permission", name = "enable-interceptors", havingValue = "true")
	public PermissionRequestInterceptor permissionRequestInterceptor(
			PermissionService permissionService,
			PermissionProperties permissionProperties,
			PermissionContextFactory permissionContextFactory) {
		return new PermissionRequestInterceptor(permissionService, permissionProperties, permissionContextFactory);
	}

	@Bean
	@Conditional(OnR4Condition.class)
	@ConditionalOnProperty(prefix = "hapi.fhir.permission", name = "enable-interceptors", havingValue = "true")
	public PermissionResponseInterceptor permissionResponseInterceptor(
			PermissionService permissionService,
			PermissionProperties permissionProperties,
			PermissionContextFactory permissionContextFactory) {
		return new PermissionResponseInterceptor(permissionService, permissionProperties, permissionContextFactory);
	}
}
