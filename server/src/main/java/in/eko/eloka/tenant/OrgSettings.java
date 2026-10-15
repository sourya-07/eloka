package in.eko.eloka.tenant;

import java.util.Optional;
import in.eko.eloka.shared.security.Caller;

/**
 * Private per-org business settings (not shown to the public like {@link OrgView#metadata()}),
 * e.g. whether account verification is on. Values are small JSON documents.
 */
public interface OrgSettings {

	Optional<String> get(long orgId, String key);

	void put(long orgId, String key, String jsonValue, Caller actor);
}
