/**
 * Append-only audit trail with a hash chain.
 *
 * <p>Status: built (P1). Public API: {@link in.eko.eloka.audit.AuditLog}.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Audit")
package in.eko.eloka.audit;

import org.springframework.modulith.ApplicationModule;
