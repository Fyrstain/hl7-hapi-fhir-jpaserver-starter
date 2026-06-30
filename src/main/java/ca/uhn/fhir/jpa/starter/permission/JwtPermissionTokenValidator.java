package ca.uhn.fhir.jpa.starter.permission;

import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JwtPermissionTokenValidator implements PermissionTokenValidator {

	private final PermissionProperties.TokenValidation myTokenValidation;

	public JwtPermissionTokenValidator(PermissionProperties theProperties) {
		myTokenValidation = theProperties.getTokenValidation();
	}

	@Override
	public PermissionValidatedToken validate(String token) {
		String normalizedToken = normalizeToken(token);
		if (!StringUtils.hasText(normalizedToken)) {
			return PermissionValidatedToken.anonymous();
		}

		SignedJWT signedJwt = parseJwt(normalizedToken);
		verifySignature(signedJwt);
		validateClaims(signedJwt);
		try {
			return new PermissionValidatedToken(normalizedToken, signedJwt.getJWTClaimsSet().getClaims());
		} catch (ParseException e) {
			throw new AuthenticationException("Invalid authentication token claims");
		}
	}

	public static String normalizeToken(String token) {
		if (!StringUtils.hasText(token)) {
			return null;
		}
		String normalized = token.trim();
		if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return normalized.substring(7).trim();
		}
		return normalized;
	}

	private SignedJWT parseJwt(String normalizedToken) {
		try {
			return SignedJWT.parse(normalizedToken);
		} catch (ParseException e) {
			throw new AuthenticationException("Malformed authentication token");
		}
	}

	private void verifySignature(SignedJWT signedJwt) {
		String mode = myTokenValidation.getMode() != null
				? myTokenValidation.getMode().trim().toLowerCase(Locale.ROOT)
				: "none";
		try {
			boolean verified = switch (mode) {
				case "hmac" -> verifyHmacSignature(signedJwt);
				case "jwk-set-uri" -> verifyWithJwkSet(signedJwt);
				default -> throw new IllegalStateException("Unsupported permission token validation mode: " + mode);
			};
			if (!verified) {
				throw new AuthenticationException("Invalid authentication token signature");
			}
		} catch (JOSEException e) {
			throw new AuthenticationException("Unable to validate authentication token");
		}
	}

	private boolean verifyHmacSignature(SignedJWT signedJwt) throws JOSEException {
		if (!StringUtils.hasText(myTokenValidation.getSharedSecret())) {
			throw new IllegalStateException("hapi.fhir.permission.token-validation.shared-secret is required in hmac mode");
		}
		return signedJwt.verify(new MACVerifier(myTokenValidation.getSharedSecret().getBytes(StandardCharsets.UTF_8)));
	}

	private boolean verifyWithJwkSet(SignedJWT signedJwt) throws JOSEException {
		if (!StringUtils.hasText(myTokenValidation.getJwkSetUri())) {
			throw new IllegalStateException("hapi.fhir.permission.token-validation.jwk-set-uri is required in jwk-set-uri mode");
		}

		JWKSet jwkSet;
		try {
			jwkSet = JWKSet.load(new URL(myTokenValidation.getJwkSetUri()));
		} catch (ParseException | MalformedURLException e) {
			throw new IllegalStateException("Unable to load the configured permission JWK set", e);
		} catch (java.io.IOException e) {
			throw new AuthenticationException("Unable to reach the configured JWK set");
		}

		JWK jwk = resolveJwk(jwkSet.getKeys(), signedJwt);
		if (jwk == null) {
			throw new AuthenticationException("No verification key found for authentication token");
		}

		JWSVerifier verifier;
		if (jwk instanceof RSAKey rsaKey) {
			verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
		} else if (jwk instanceof ECKey ecKey) {
			verifier = new ECDSAVerifier(ecKey);
		} else if (jwk instanceof OctetSequenceKey octetSequenceKey) {
			verifier = new MACVerifier(octetSequenceKey.toByteArray());
		} else {
			throw new AuthenticationException("Unsupported JWK type for authentication token validation");
		}
		return signedJwt.verify(verifier);
	}

	private JWK resolveJwk(List<JWK> keys, SignedJWT signedJwt) {
		String keyId = signedJwt.getHeader().getKeyID();
		if (keyId != null) {
			for (JWK key : keys) {
				if (keyId.equals(key.getKeyID())) {
					return key;
				}
			}
		}
		return keys.isEmpty() ? null : keys.get(0);
	}

	private void validateClaims(SignedJWT signedJwt) {
		try {
			JWTClaimsSet claimsSet = signedJwt.getJWTClaimsSet();
			Instant now = Instant.now();

			if (claimsSet.getExpirationTime() == null || claimsSet.getExpirationTime().toInstant().isBefore(now)) {
				throw new AuthenticationException("Authentication token is expired");
			}
			if (claimsSet.getNotBeforeTime() != null && claimsSet.getNotBeforeTime().toInstant().isAfter(now)) {
				throw new AuthenticationException("Authentication token is not yet valid");
			}
			if (StringUtils.hasText(myTokenValidation.getIssuer())
					&& !myTokenValidation.getIssuer().equals(claimsSet.getIssuer())) {
				throw new AuthenticationException("Authentication token issuer is invalid");
			}
			if (StringUtils.hasText(myTokenValidation.getAudience())
					&& (claimsSet.getAudience() == null || !claimsSet.getAudience().contains(myTokenValidation.getAudience()))) {
				throw new AuthenticationException("Authentication token audience is invalid");
			}
		} catch (ParseException e) {
			throw new AuthenticationException("Invalid authentication token claims");
		}
	}
}
