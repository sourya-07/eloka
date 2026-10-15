/**
 * Ports and adapters for EPS and external providers, resilience, call log and webhook inbox.
 *
 * <p>Status: planned. See docs/onboarding/ARCHITECTURE.md §4 at the repo root.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Integration")
package in.eko.eloka.integration;

import org.springframework.modulith.ApplicationModule;
