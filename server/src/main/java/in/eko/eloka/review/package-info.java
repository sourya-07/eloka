/**
 * Staff review queue, checklist, corrections, holds and maker-checker approval.
 *
 * <p>Status: planned. See docs/onboarding/ARCHITECTURE.md §4 at the repo root.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Review")
package in.eko.eloka.review;

import org.springframework.modulith.ApplicationModule;
