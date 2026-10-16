package in.eko.eloka.gateway.internal.handler.onboarding;

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
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.OtpDispatch;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.DomainException;
import in.eko.eloka.shared.security.Caller;
import org.springframework.stereotype.Component;

/**
 * Assisted onboarding: a distributor/admin adds an agent (194), the agent confirms with an OTP
 * sent to their phone (197), then the normal role and KYC steps run for that agent.
 */
final class AssistedHandlers {

	private AssistedHandlers() {
	}

	/** 194: 874 = already in your network (go to status check), 873 = OTP sent to the agent. */
	@Component
	static class AddAgent implements EpsHandler {

		private final IdentityApi identity;
		private final UserDirectory users;
		private final NetworkQueries network;

		AddAgent(IdentityApi identity, UserDirectory users, NetworkQueries network) {
			this.identity = identity;
			this.users = users;
			this.network = network;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(194));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			String mobile = Mobiles.normalize(call.params().requireString("csp_id"));
			Optional<UserView> existing = users.findByMobile(caller.orgId(), mobile);
			if (existing.isPresent()) {
				try {
					network.findInScope(caller, mobile);
					return EpsEnvelope.ok().type(874).data(Map.of("csp_id", mobile)).message("Agent already exists").build();
				}
				catch (DomainException outOfScope) {
					return EpsEnvelope.fail("This mobile number is already registered.")
						.put("invalid_params", Map.of("csp_id", "Already registered"))
						.build();
				}
			}
			OtpDispatch sent = identity.sendAgentConsentOtp(caller.orgId(), mobile, caller.userId(), call.request().clientIp());
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("csp_id", mobile);
			if (sent.revealedCode() != null) {
				data.put("otp", sent.revealedCode());
			}
			return EpsEnvelope.ok().type(873).message("OTP sent to the agent").data(data).build();
		}
	}

	/** 197: verify the agent's OTP. 876 = verified, 302 = wrong OTP. */
	@Component
	static class VerifyAgentOtp implements EpsHandler {

		private final IdentityApi identity;

		VerifyAgentOtp(IdentityApi identity) {
			this.identity = identity;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(197));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			String mobile = Mobiles.normalize(call.params().requireString("csp_id"));
			if (identity.verifyAgentConsentOtp(caller.orgId(), mobile, caller.userId(), call.params().string("otp").orElse(""))) {
				return EpsEnvelope.ok().type(876).data(Map.of("csp_id", mobile)).message("Agent verified").build();
			}
			return EpsEnvelope.fail("Wrong OTP. Please check with the agent and try again.").type(302).build();
		}
	}
}
