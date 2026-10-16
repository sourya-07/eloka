package in.eko.eloka.integration;

import java.util.Optional;

/** Verifies a Google sign-in ID token (signature, issuer, audience, expiry) for staff login. */
public interface GoogleIdentityVerifier {

	/**
	 * @return the verified identity, or empty when the token is invalid
	 * @throws in.eko.eloka.shared.error.ConflictException {@code PROVIDER_NOT_CONFIGURED} without a client id
	 */
	Optional<GoogleIdentity> verify(String idToken);

	/** @param hostedDomain the Workspace domain ({@code hd} claim), null for personal accounts */
	record GoogleIdentity(String email, boolean emailVerified, String hostedDomain, String name) {
	}
}
