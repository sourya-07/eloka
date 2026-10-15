package in.eko.eloka.gateway.internal.handler.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.OtpDispatch;
import org.springframework.stereotype.Component;

/** 515: send a login OTP. The same answer whether or not the mobile has an account. */
@Component
class SendOtpHandler implements EpsHandler {

	private final IdentityApi identity;
	private final OrgTokenParam orgToken;

	SendOtpHandler(IdentityApi identity, OrgTokenParam orgToken) {
		this.identity = identity;
		this.orgToken = orgToken;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.interaction(515));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.PUBLIC;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		long orgId = orgToken.requireOrgId(call.params());
		OtpDispatch sent = identity.sendLoginOtp(orgId, call.params().requireString("mobile"), call.request().clientIp());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("mobile", sent.mobile());
		data.put("otp_length", sent.length());
		if (sent.revealedCode() != null) {
			data.put("otp", sent.revealedCode());
		}
		return EpsEnvelope.ok().message("OTP sent").data(data).build();
	}
}
