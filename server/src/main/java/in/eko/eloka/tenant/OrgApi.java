package in.eko.eloka.tenant;

import java.util.Optional;
import in.eko.eloka.shared.security.Caller;

/** Public API of the tenant module. */
public interface OrgApi {

	/** @param hostname full host name, case-insensitive */
	Optional<OrgView> findByHostname(String hostname);

	/** @throws in.eko.eloka.shared.error.NotFoundException when missing */
	OrgView getById(long orgId);

	/** Builds the full host name for a sub-domain label of the platform root domain. */
	String hostnameForSubdomain(String label);

	OrgView createOrg(NewOrg org);

	/** Replaces one public metadata entry (e.g. {@code theme}) after validating it is JSON. */
	void saveMetadata(long orgId, String key, String jsonValue, Caller actor);
}
