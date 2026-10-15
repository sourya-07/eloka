/**
 * Applicant mobile-OTP login and sessions, staff identities from SSO, roles and permissions.
 *
 * <p>Status: login OTP and sessions built (P1). Public API: {@link in.eko.eloka.identity.IdentityApi}.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Identity")
package in.eko.eloka.identity;

import org.springframework.modulith.ApplicationModule;
