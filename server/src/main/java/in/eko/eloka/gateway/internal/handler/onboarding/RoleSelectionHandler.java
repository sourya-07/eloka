package in.eko.eloka.gateway.internal.handler.onboarding;

import java.util.List;
import java.util.Optional;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.network.UserTypes;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.shared.error.ForbiddenException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import in.eko.eloka.shared.security.SessionKind;
import org.springframework.stereotype.Component;

/**
 * 521: role selection, which creates the account.
 * <ul>
 * <li>Self sign-up: the caller's verified mobile becomes a retailer/distributor and the session
 * is upgraded to a normal one, so the same token keeps working.</li>
 * <li>Assisted: a distributor/admin creates the account for an agent who confirmed their OTP
 * (194/197) in the last 30 minutes; a distributor's agents are placed under them.</li>
 * </ul>
 * The new user's onboarding plan starts automatically. Success = 1566.
 */
@Component
class RoleSelectionHandler implements EpsHandler {

	static final int ROLE_SAVED = 1566;

	private final UserCommands userCommands;
	private final UserDirectory users;
	private final IdentityApi identity;

	RoleSelectionHandler(UserCommands userCommands, UserDirectory users, IdentityApi identity) {
		this.userCommands = userCommands;
		this.users = users;
		this.identity = identity;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.interaction(521));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.SESSION;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		Caller caller = call.caller();
		int userType = userTypeOf(call.params().string("applicant_type").orElse(""));
		String target = Optional.ofNullable(OnboardingSubjects.targetMobile(call)).orElse(caller.mobile());

		if (target.equals(caller.mobile())) {
			return selfSignUp(caller, userType);
		}
		return assisted(caller, target, userType);
	}

	private EpsResponse selfSignUp(Caller caller, int userType) {
		if (caller.kind() != SessionKind.PRE_REGISTRATION) {
			// Already registered (e.g. the page was reloaded after choosing the role).
			return EpsEnvelope.ok().type(ROLE_SAVED).message("Role saved").build();
		}
		UserView user = userCommands.createUser(newUser(caller.orgId(), caller.mobile(), userType, null));
		identity.attachUser(caller.sessionId(), user.id());
		return EpsEnvelope.ok().type(ROLE_SAVED).message("Role saved").build();
	}

	private EpsResponse assisted(Caller caller, String agentMobile, int userType) {
		if (caller.role() != Role.DISTRIBUTOR && caller.role() != Role.ORG_ADMIN) {
			throw new ForbiddenException("NOT_ALLOWED", "You are not allowed to do this.");
		}
		if (caller.role() == Role.DISTRIBUTOR && userType != UserTypes.MERCHANT) {
			throw new ForbiddenException("NOT_ALLOWED", "Distributors can onboard retailers only.");
		}
		if (users.findByMobile(caller.orgId(), agentMobile).isPresent()) {
			throw new ConflictException("MOBILE_ALREADY_REGISTERED", "This mobile number already has an account");
		}
		if (!identity.hasAgentConsent(caller.orgId(), agentMobile, caller.userId())) {
			throw new ForbiddenException("CONSENT_REQUIRED", "Verify the agent's OTP first.");
		}
		var parent = caller.role() == Role.DISTRIBUTOR ? caller.userId() : null;
		userCommands.createUser(newUser(caller.orgId(), agentMobile, userType, parent));
		return EpsEnvelope.ok().type(ROLE_SAVED).message("Role saved").build();
	}

	private static NewUser newUser(long orgId, String mobile, int userType, java.util.UUID parent) {
		// The real name arrives with the business details step.
		return new NewUser(orgId, mobile, "Agent " + mobile.substring(6), null, userType, false, UserStatus.ACTIVE, null,
				null, false, ShopView.empty(), parent);
	}

	/** applicant_type: 0 retailer, 2 distributor (3 enterprise is not offered). */
	static int userTypeOf(String applicantType) {
		return switch (applicantType.trim()) {
			case "0" -> UserTypes.MERCHANT;
			case "2" -> UserTypes.DISTRIBUTOR;
			default -> throw ValidationException.field("applicant_type", "Choose retailer or distributor");
		};
	}
}
