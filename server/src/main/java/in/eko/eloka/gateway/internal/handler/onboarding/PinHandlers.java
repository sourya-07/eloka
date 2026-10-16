package in.eko.eloka.gateway.internal.handler.onboarding;

import java.util.List;
import java.util.Map;
import in.eko.eloka.agentonboarding.AgentOnboardingApi;
import in.eko.eloka.agentonboarding.OnboardingSubject;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.PinKey;
import in.eko.eloka.network.UserDirectory;
import org.springframework.stereotype.Component;

/** Secret PIN step: booklet info (170), one-time PinTwin key (10005), set PIN (5). */
final class PinHandlers {

	private PinHandlers() {
	}

	/** 170: no physical PinTwin booklets, so the PIN is typed on the PinTwin keypad. Success 1646. */
	@Component
	static class Booklet implements EpsHandler {

		private final OnboardingSubjects subjects;
		private final UserDirectory users;

		Booklet(OnboardingSubjects subjects, UserDirectory users) {
			this.subjects = subjects;
			this.users = users;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(170));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			OnboardingSubject s = subjects.resolve(call);
			String userCode = users.getById(s.orgId(), s.userId()).userCode();
			return EpsEnvelope.ok().type(1646)
				.data(Map.of("user_code", userCode, "booklet_serial_number", "", "is_pintwin_user", 0))
				.build();
		}
	}

	/** 10005: a fresh one-time key for the PinTwin keypad, bound to this session. */
	@Component
	static class PinTwinKeyHandler implements EpsHandler {

		private final IdentityApi identity;

		PinTwinKeyHandler(IdentityApi identity) {
			this.identity = identity;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(10005));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			PinKey key = identity.issuePinKey(call.caller().sessionId());
			return EpsEnvelope.ok().data(Map.of("pintwin_key", key.key(), "key_id", key.keyId())).build();
		}
	}

	/** 5: set the secret PIN from the two encoded entries. Success 9. */
	@Component
	static class SetPin implements EpsHandler {

		private final OnboardingSubjects subjects;
		private final IdentityApi identity;
		private final AgentOnboardingApi onboarding;

		SetPin(OnboardingSubjects subjects, IdentityApi identity, AgentOnboardingApi onboarding) {
			this.subjects = subjects;
			this.identity = identity;
			this.onboarding = onboarding;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(5));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			OnboardingSubject s = subjects.resolve(call);
			identity.setTransactionPin(s.orgId(), s.userId(), call.caller().sessionId(),
					call.params().requireString("first_okekey"), call.params().requireString("second_okekey"));
			onboarding.markPinSet(s);
			return EpsEnvelope.ok().type(9).message("PIN set").build();
		}
	}
}
