package in.eko.eloka.system;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.tenant.NewOrg;
import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.tenant.OrgStatus;
import in.eko.eloka.tenant.OrgView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first tenant of an installation: org, sub-domain (+ custom domains), default
 * branding metadata and its Local Admin. Idempotent: an existing sub-domain is left untouched.
 * Until Release 2 provisioning exists, this is how production gets its first tenant.
 */
@Service
class TenantBootstrap {

	/** The admin user type and code labels the client expects (same as the local seed). */
	private static final int ADMIN_USER_TYPE = 24;

	private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

	private static final Map<String, String> DEFAULT_METADATA = Map.of(
			"theme", """
					{"navstyle":"dark","primary":"#015a8c","primary_dark":"#024d78","primary_light":"#0167a1",\
					"accent":"#ed7802","accent_dark":"#be5f00","accent_light":"#f19333"}""",
			"login_meta", "{\"mobile_mapped_user_id\":0,\"user_id_label\":\"User ID\"}",
			"user_type_labels", """
					{"1":{"en":"Distributor"},"2":{"en":"Retailer"},"3":{"en":"I-Merchant"},\
					"4":{"en":"Field Executive"},"24":{"en":"Admin"}}""",
			"user_code_labels", "{\"2\":\"Retailer Code\",\"3\":\"Retailer Code\"}");

	/** What happened, for the runner's log line. */
	record Outcome(boolean created, long orgId, String hostname) {
	}

	private final OrgApi orgs;
	private final UserCommands users;

	TenantBootstrap(OrgApi orgs, UserCommands users) {
		this.orgs = orgs;
		this.users = users;
	}

	@Transactional
	Outcome run(BootstrapProperties request) {
		String label = require(request.subdomain(), "subdomain").toLowerCase();
		if (!LABEL.matcher(label).matches()) {
			throw ValidationException.field("subdomain", "Use lowercase letters, digits and hyphens (e.g. shlok-enterprise)");
		}
		String hostname = orgs.hostnameForSubdomain(label);
		var existing = orgs.findByHostname(hostname);
		if (existing.isPresent()) {
			return new Outcome(false, existing.get().id(), hostname);
		}
		String orgName = require(request.orgName(), "org-name");
		String mobile = Mobiles.normalize(require(request.adminMobile(), "admin-mobile"));
		String adminName = require(request.adminName(), "admin-name");

		List<String> hostnames = new ArrayList<>(List.of(hostname));
		request.extraHostnames().stream().map(h -> h.trim().toLowerCase()).filter(h -> !h.isEmpty()).forEach(hostnames::add);
		Map<String, String> metadata = new LinkedHashMap<>(DEFAULT_METADATA);
		if (request.supportEmail() != null || request.supportPhone() != null) {
			metadata.put("support_contacts", "{\"email\":%s,\"phone\":%s}".formatted(quote(request.supportEmail()),
					quote(request.supportPhone())));
		}
		String appName = request.appName() == null || request.appName().isBlank() ? orgName : request.appName().trim();
		OrgView org = orgs.createOrg(new NewOrg(orgName.trim(), appName, null, OrgStatus.ACTIVE, hostnames, metadata));
		users.createUser(new NewUser(org.id(), mobile, adminName.trim(), blankToNull(request.adminEmail()), ADMIN_USER_TYPE,
				true, UserStatus.ACTIVE, null, null, false, ShopView.empty(), null));
		return new Outcome(true, org.id(), hostname);
	}

	private static String require(String value, String name) {
		if (value == null || value.isBlank()) {
			throw ValidationException.field(name, "ELOKA_BOOTSTRAP_" + name.toUpperCase().replace('-', '_') + " is required");
		}
		return value.trim();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String quote(String value) {
		return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
