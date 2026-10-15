/**
 * Post-approval saga that creates the tenant and the Local Admin in EPS, with retries.
 *
 * <p>Status: planned. See docs/onboarding/ARCHITECTURE.md §4 at the repo root.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Provisioning")
package in.eko.eloka.provisioning;

import org.springframework.modulith.ApplicationModule;
