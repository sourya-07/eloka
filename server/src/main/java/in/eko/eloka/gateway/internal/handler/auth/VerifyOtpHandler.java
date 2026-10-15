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
import in.eko.eloka.identity.LoginResult;
import org.springframework.stereotype.Component;

/**
 * 518: check the OTP and open a session. The client keeps
 * {@code data.otp_verification_token} as its access token; the extra token fields are for
 * the refresh flow (FE-3).
 */
@Component
class VerifyOtpHandler implements EpsHandler {

	static final int OTP_VERIFIED = 1541;

	private final IdentityApi identity;
	private final OrgTokenParam orgToken;

	VerifyOtpHandler(IdentityApi identity, OrgTokenParam orgToken) {
		this.identity = identity;
		this.orgToken = orgToken;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.interaction(518));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.PUBLIC;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		long orgId = orgToken.requireOrgId(call.params());
		String mobile = call.params().requireString("mobile");
		String code = call.params().string("otp").or(() -> call.params().string("id_token")).orElse("");

		LoginResult result = identity.verifyLoginOtp(orgId, mobile, code);
		return switch (result) {
			case LoginResult.Success success -> {
				Map<String, Object> data = new LinkedHashMap<>();
				data.put("otp_verification_token", success.tokens().accessToken());
				data.put("access_token", success.tokens().accessToken());
				data.put("refresh_token", success.tokens().refreshToken());
				data.put("token_expiration", success.tokens().expiresIn().toSeconds());
				data.put("is_registered", success.registered() ? 1 : 0);
				yield EpsEnvelope.ok().type(OTP_VERIFIED).message("OTP verified").data(data).build();
			}
			case LoginResult.WrongCode wrong -> EpsEnvelope
				.fail(wrong.attemptsLeft() > 0 ? "Wrong OTP. " + wrong.attemptsLeft() + " attempts left."
						: "Wrong OTP. Please request a new one.")
				.put("otpFailed", true)
				.build();
			case LoginResult.Expired expired -> EpsEnvelope.fail("OTP expired. Please request a new one.")
				.put("otpFailed", true)
				.build();
			case LoginResult.AccountInactive inactive -> EpsEnvelope.fail("Your account is not active.")
				.put("accountInactive", true)
				.build();
		};
	}
}
