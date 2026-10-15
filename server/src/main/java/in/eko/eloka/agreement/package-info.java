/**
 * Agreement templates, rendering, e-sign sessions and signed-PDF archive.
 *
 * <p>Status: planned. See docs/onboarding/ARCHITECTURE.md §4 at the repo root.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Agreement")
package in.eko.eloka.agreement;

import org.springframework.modulith.ApplicationModule;
