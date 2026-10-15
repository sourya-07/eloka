package in.eko.eloka.gateway.internal.handler.org;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.tenant.OrgStatus;
import in.eko.eloka.tenant.OrgTokens;
import in.eko.eloka.tenant.OrgView;
import in.eko.eloka.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 677: org lookup by host, called server-side by the client (ORG_META_SERVICE) on every
 * page load. Requires the shared secret. Unknown or inactive org → {@code response_type_id 1829}.
 */
@Component
class OrgLookupHandler implements EpsHandler {

	static final int ORG_NOT_FOUND = 1829;
	private static final Logger log = LoggerFactory.getLogger(OrgLookupHandler.class);

	private final OrgApi orgs;
	private final OrgTokens orgTokens;
	private final TenantProperties properties;
	private final JsonMapper json;

	OrgLookupHandler(OrgApi orgs, OrgTokens orgTokens, TenantProperties properties, JsonMapper json) {
		this.orgs = orgs;
		this.orgTokens = orgTokens;
		this.properties = properties;
		this.json = json;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.interaction(677));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.PUBLIC;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		String secret = call.params().string("secret").orElse("");
		if (!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
				properties.orgMetaSecret().getBytes(StandardCharsets.UTF_8))) {
			log.warn("Org lookup with a wrong secret from {}", call.request().clientIp());
			return EpsEnvelope.fail("Not allowed").build();
		}
		Optional<String> hostname = call.params().string("sub_domain")
			.map(label -> orgs.hostnameForSubdomain(decode(label)))
			.or(() -> call.params().string("domain").map(OrgLookupHandler::decode));

		Optional<OrgView> org = hostname.flatMap(orgs::findByHostname).filter(o -> o.status() == OrgStatus.ACTIVE);
		if (org.isEmpty()) {
			return EpsEnvelope.fail("Organisation not found").type(ORG_NOT_FOUND).build();
		}
		return EpsEnvelope.ok().data(orgData(org.get())).put("org_token", orgTokens.issue(org.get().id())).build();
	}

	private Map<String, Object> orgData(OrgView org) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		org.metadata().forEach((key, value) -> metadata.put(key, parse(value)));

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("org_id", org.id());
		data.put("app_name", org.appName());
		data.put("org_name", org.name());
		data.put("logo", org.logoUrl());
		data.put("metadata", metadata);
		data.put("login_types", Map.of("google", Map.of("default", false)));
		return data;
	}

	private Object parse(String value) {
		try {
			return json.readTree(value);
		}
		catch (JacksonException ex) {
			return value;
		}
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8).trim().toLowerCase();
	}
}
