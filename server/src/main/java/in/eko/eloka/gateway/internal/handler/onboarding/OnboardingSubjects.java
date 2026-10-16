package in.eko.eloka.gateway.internal.handler.onboarding;

import in.eko.eloka.agentonboarding.OnboardingSubject;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ForbiddenException;
import in.eko.eloka.shared.error.UnauthenticatedException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import org.springframework.stereotype.Component;

/**
 * Whose onboarding a step call is for. The agent's mobile comes in {@code mobile},
 * {@code user_id} or {@code csp_id}; when it is the caller's own, it is self-onboarding,
 * otherwise the caller must be a distributor/admin with that agent in their network.
 */
@Component
class OnboardingSubjects {

	private final NetworkQueries network;

	OnboardingSubjects(NetworkQueries network) {
		this.network = network;
	}

	OnboardingSubject resolve(EpsCall call) {
		Caller caller = call.caller();
		if (!caller.isRegisteredUser()) {
			throw new UnauthenticatedException("Please choose your role first.");
		}
		String target = targetMobile(call);
		if (target == null || target.equals(caller.mobile())) {
			return new OnboardingSubject(caller.orgId(), caller.userId(), caller.userId());
		}
		if (caller.role() != Role.DISTRIBUTOR && caller.role() != Role.ORG_ADMIN) {
			throw new ForbiddenException("NOT_ALLOWED", "You can only complete your own onboarding.");
		}
		UserView agent = network.findInScope(caller, target);
		return new OnboardingSubject(caller.orgId(), agent.id(), caller.userId());
	}

	/** The agent mobile in the request, normalised, or null. */
	static String targetMobile(EpsCall call) {
		return call.params().string("mobile")
			.or(() -> call.params().string("user_id"))
			.or(() -> call.params().string("csp_id"))
			.filter(value -> value.replaceAll("\\D", "").length() >= 10)
			.map(Mobiles::normalize)
			.orElse(null);
	}
}
