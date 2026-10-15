package in.eko.eloka.gateway.internal.handler.org;

import java.util.List;
import java.util.Set;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.tenant.OrgApi;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 774: org admin updates public metadata (Configurations → Theme). Body:
 * {@code orgDetails.metadata[{name, type, value}]}. Only allow-listed keys can be changed.
 */
@Component
class OrgMetadataHandler implements EpsHandler {

	private static final Set<String> EDITABLE = Set.of("theme", "support_contacts", "cms_meta", "cms_data");

	private final OrgApi orgs;
	private final JsonMapper json;

	OrgMetadataHandler(OrgApi orgs, JsonMapper json) {
		this.orgs = orgs;
		this.json = json;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.interaction(774));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.ORG_ADMIN;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		Caller caller = call.caller();
		JsonNode entries = call.params().node("orgDetails").map(details -> details.path("metadata"))
			.orElseThrow(() -> ValidationException.field("orgDetails", "orgDetails.metadata is required"));
		if (!entries.isArray() || entries.isEmpty()) {
			throw ValidationException.field("orgDetails", "orgDetails.metadata must be a non-empty list");
		}
		for (JsonNode entry : entries) {
			String name = entry.path("name").asString("");
			if (!EDITABLE.contains(name)) {
				throw ValidationException.field(name, "This setting cannot be changed here");
			}
			JsonNode value = entry.path("value");
			String jsonValue = value.isString() ? value.asString() : json.writeValueAsString(value);
			orgs.saveMetadata(caller.orgId(), name, jsonValue, caller);
		}
		return EpsEnvelope.ok().message("Saved").build();
	}
}
