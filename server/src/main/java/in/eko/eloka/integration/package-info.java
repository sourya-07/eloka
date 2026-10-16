/**
 * Ports to external providers (DigiLocker, e-sign) with their adapters. Local and test runs
 * use fakes (marked {@code UnsafeInProduction}); other environments get "not configured"
 * adapters until a real provider contract exists, so nothing silently pretends to verify.
 *
 * <p>Public API: {@link in.eko.eloka.integration.DigiLockerProvider},
 * {@link in.eko.eloka.integration.EsignProvider}.
 */
@ApplicationModule(displayName = "Integration")
package in.eko.eloka.integration;

import org.springframework.modulith.ApplicationModule;
