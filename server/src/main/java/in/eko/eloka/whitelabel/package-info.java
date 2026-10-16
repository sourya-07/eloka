/**
 * Branding drafts and sub-domain availability and reservation.
 *
 * <p>Sub-domain reservations and branding drafts during onboarding (P7); binding and publishing at
 * provisioning (P9). Design: docs/onboarding/P7_APPLICANT_API.md.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "White label")
package in.eko.eloka.whitelabel;

import org.springframework.modulith.ApplicationModule;
