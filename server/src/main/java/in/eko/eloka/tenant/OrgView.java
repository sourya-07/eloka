package in.eko.eloka.tenant;

import java.util.Map;

/**
 * Read-only view of a tenant.
 *
 * @param id numeric org id (the "org_id" the client knows)
 * @param name legal/display name
 * @param appName brand name shown in the app
 * @param logoUrl logo URL, may be null
 * @param status lifecycle state
 * @param metadata public metadata entries (theme, login_meta, ...) as raw JSON strings
 */
public record OrgView(long id, String name, String appName, String logoUrl, OrgStatus status,
		Map<String, String> metadata) {

	public OrgView {
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
