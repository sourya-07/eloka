/**
 * The onboarding application aggregate: steps, step policy, state machine, resume and expiry.
 *
 * <p>Status: planned. See docs/onboarding/ARCHITECTURE.md §4 at the repo root.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Onboarding")
package in.eko.eloka.onboarding;

import org.springframework.modulith.ApplicationModule;
