package in.eko.eloka.gateway.internal.handler.auth;

import java.util.List;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ForbiddenException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import org.springframework.stereotype.Component;

/**
 * 151: user profile. Returns 369 + the profile, or 370 when the mobile has no account (the
 * client then starts self-onboarding). A caller may read their own profile; an org admin may
 * read any user of their org.
 */
@Component
class ProfileHandler implements EpsHandler {

	static final int PROFILE_FOUND = 369;
	static final int PROFILE_NOT_FOUND = 370;

	private final UserDirectory users;
	private final ProfileAssembler assembler;

	ProfileHandler(UserDirectory users, ProfileAssembler assembler) {
		this.users = users;
		this.assembler = assembler;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.interaction(151));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.SESSION;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		Caller caller = call.caller();
		String requested = call.params().string("mobile").map(Mobiles::normalize).orElse(caller.mobile());
		if (!requested.equals(caller.mobile()) && caller.role() != Role.ORG_ADMIN) {
			throw new ForbiddenException("NOT_ALLOWED", "You can only view your own profile.");
		}
		return users.findByMobile(caller.orgId(), requested)
			.map(UserView::id)
			.map(id -> users.profile(caller.orgId(), id))
			.map(profile -> EpsEnvelope.ok().type(PROFILE_FOUND).message("Profile found").data(assembler.profileData(profile)).build())
			.orElseGet(() -> EpsEnvelope.fail("User not found").type(PROFILE_NOT_FOUND).build());
	}
}
