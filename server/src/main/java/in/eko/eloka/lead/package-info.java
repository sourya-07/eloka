/**
 * Leads, qualification and onboarding invites (staff only).
 *
 * <p>Status: planned. See docs/onboarding/ARCHITECTURE.md §4 at the repo root.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Lead")
package in.eko.eloka.lead;

import org.springframework.modulith.ApplicationModule;
