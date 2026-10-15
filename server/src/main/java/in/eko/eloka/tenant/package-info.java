/**
 * Live tenants, domains, branding versions, Local Admins, go-live tasks and activation.
 *
 * <p>Status: orgs, domains, metadata and org tokens built (P1). Public API: {@link in.eko.eloka.tenant.OrgApi}, {@link in.eko.eloka.tenant.OrgTokens}.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Tenant")
package in.eko.eloka.tenant;

import org.springframework.modulith.ApplicationModule;
