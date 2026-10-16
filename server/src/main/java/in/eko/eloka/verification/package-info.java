/**
 * PAN, signatory KYC (DigiLocker), bank, email and name-match verifications.
 *
 * <p>Built in P7 for Local Admin applications (docs/onboarding/P7_APPLICANT_API.md); providers sit behind
 * ports in {@code integration} with local fakes.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Verification")
package in.eko.eloka.verification;

import org.springframework.modulith.ApplicationModule;
