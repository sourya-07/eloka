package in.eko.eloka.integration.internal;

import java.util.List;
import java.util.Optional;
import in.eko.eloka.integration.GoogleIdentityVerifier;
import in.eko.eloka.shared.error.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Real Google ID token check: RS256 signature against Google's published keys, issuer
 * {@code accounts.google.com}, audience = the staff console's client id, not expired.
 */
@Component
@Profile("!test")
class GoogleIdTokenVerifier implements GoogleIdentityVerifier {

	private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
	private static final List<String> ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

	/** Same setting as {@code StaffProperties.googleClientId} (read here so integration does not depend on identity). */
	private final String clientId;
	private volatile NimbusJwtDecoder decoder;

	GoogleIdTokenVerifier(@Value("${eloka.staff.google-client-id:}") String clientId) {
		this.clientId = clientId;
	}

	@Override
	public Optional<GoogleIdentity> verify(String idToken) {
		if (clientId == null || clientId.isBlank()) {
			throw new ConflictException("PROVIDER_NOT_CONFIGURED", "Staff Google sign-in is not configured.");
		}
		try {
			Jwt jwt = decoder(clientId).decode(idToken);
			Object verified = jwt.getClaims().get("email_verified");
			return Optional.of(new GoogleIdentity(jwt.getClaimAsString("email"),
					Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)), jwt.getClaimAsString("hd"),
					jwt.getClaimAsString("name")));
		}
		catch (JwtException ex) {
			return Optional.empty();
		}
	}

	private NimbusJwtDecoder decoder(String clientId) {
		NimbusJwtDecoder current = decoder;
		if (current == null) {
			current = NimbusJwtDecoder.withJwkSetUri(JWKS).build();
			OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(String.valueOf(jwt.getClaims().get("iss")))
					? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected issuer", null));
			OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
					? OAuth2TokenValidatorResult.success()
					: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
			current.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience));
			decoder = current;
		}
		return current;
	}
}
