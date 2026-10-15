/**
 * PAN, signatory KYC (DigiLocker), bank, email and name-match verifications.
 *
 * <p>Status: planned. See docs/onboarding/ARCHITECTURE.md §4 at the repo root.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Verification")
package in.eko.eloka.verification;

import org.springframework.modulith.ApplicationModule;
