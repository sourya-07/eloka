package in.eko.eloka.gateway.internal.handler.auth;

import in.eko.eloka.gateway.internal.envelope.EpsParams;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.tenant.OrgTokens;
import org.springframework.stereotype.Component;

/** Reads the tenant of a public call from its {@code org_token} (never from {@code org_id}). */
@Component
public class OrgTokenParam {

	private final OrgTokens orgTokens;

	public OrgTokenParam(OrgTokens orgTokens) {
		this.orgTokens = orgTokens;
	}

	public long requireOrgId(EpsParams params) {
		return params.string("org_token")
			.flatMap(orgTokens::verify)
			.orElseThrow(() -> ValidationException.field("org_token", "Please reload the page and try again."));
	}
}
