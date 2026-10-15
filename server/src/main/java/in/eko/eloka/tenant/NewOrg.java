package in.eko.eloka.tenant;

import java.util.List;
import java.util.Map;

/**
 * Data to create a tenant (used by provisioning and the local seeder).
 *
 * @param hostnames full host names that resolve to this org, e.g. {@code demo.eloka.in}
 * @param metadata public metadata entries as raw JSON strings
 */
public record NewOrg(String name, String appName, String logoUrl, OrgStatus status, List<String> hostnames,
		Map<String, String> metadata) {
}
