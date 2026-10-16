package in.eko.eloka.gateway.internal.handler.auth;

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
import in.eko.eloka.identity.TokenPair;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.CallerResolver;
import in.eko.eloka.shared.security.SessionKind;
import org.springframework.stereotype.Component;

/** The {@code /authentication/*} endpoints. */
final class SessionRoutes {

	private SessionRoutes() {
	}

	/** Builds the user object for a session, for token and refresh-profile responses. */
	@Component
	static class SessionObjects {

		private final UserDirectory users;
		private final ProfileAssembler assembler;

		SessionObjects(UserDirectory users, ProfileAssembler assembler) {
			this.users = users;
			this.assembler = assembler;
		}

		Map<String, Object> of(Caller caller, TokenPair tokens) {
			if (caller.kind() == SessionKind.PRE_REGISTRATION) {
				return assembler.preRegistrationObject(caller.mobile(), tokens);
			}
			return assembler.sessionObject(users.profile(caller.orgId(), caller.userId()), tokens);
		}
	}

	/** Logout. Always succeeds, so it reveals nothing about tokens. */
	@Component
	static class Revoke implements EpsHandler {

		private final IdentityApi identity;

		Revoke(IdentityApi identity) {
			this.identity = identity;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.path("/authentication/revoke"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.PUBLIC;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			call.request().bearerToken().ifPresent(token -> identity.revoke(token, "LOGOUT"));
			call.params().string("refresh_token").ifPresent(token -> identity.revoke(token, "LOGOUT"));
			return EpsEnvelope.ok().message("Logged out").build();
		}
	}

	/**
	 * Refresh-token rotation. An invalid token answers HTTP 200 + failure: the current client
	 * sends {@code "undefined"} after a reload and logs the user out on any non-2xx.
	 */
	@Component
	static class Token implements EpsHandler {

		private final IdentityApi identity;
		private final CallerResolver callers;
		private final SessionObjects objects;

		Token(IdentityApi identity, CallerResolver callers, SessionObjects objects) {
			this.identity = identity;
			this.callers = callers;
			this.objects = objects;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.path("/authentication/token"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.PUBLIC;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Optional<TokenPair> tokens = call.params().string("refresh_token").flatMap(identity::refresh);
			Optional<Caller> caller = tokens.flatMap(pair -> callers.resolve(pair.accessToken()));
			if (tokens.isEmpty() || caller.isEmpty()) {
				return EpsEnvelope.fail("Session expired. Please log in again.").build();
			}
			// Bare object, not an envelope: the client passes the response straight to its user store.
			return EpsResponse.Json.ok(objects.of(caller.get(), tokens.get()));
		}
	}

	/** Fresh tokens + the latest user object for the logged-in user. */
	@Component
	static class RefreshProfile implements EpsHandler {

		private final IdentityApi identity;
		private final SessionObjects objects;
		private final in.eko.eloka.network.NetworkQueries network;
		private final UserDirectory users;
		private final ProfileAssembler assembler;

		RefreshProfile(IdentityApi identity, SessionObjects objects, in.eko.eloka.network.NetworkQueries network,
				UserDirectory users, ProfileAssembler assembler) {
			this.identity = identity;
			this.objects = objects;
			this.network = network;
			this.users = users;
			this.assembler = assembler;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.path("/authentication/refresh-profile"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.SESSION;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			java.util.Optional<String> agent = call.params().string("csp_id");
			if (agent.isPresent()) {
				return agentStatus(caller, agent.get());
			}
			TokenPair tokens = identity.rotate(caller.sessionId());
			return EpsResponse.Json.ok(objects.of(caller, tokens));
		}

		/**
		 * Assisted onboarding status check: {@code {details: {user_detail}}} for an agent in the
		 * caller's network; no details when the agent has no account yet (the client then starts
		 * role selection).
		 */
		private EpsResponse agentStatus(Caller caller, String agentMobile) {
			if (caller.role() != in.eko.eloka.shared.security.Role.DISTRIBUTOR
					&& caller.role() != in.eko.eloka.shared.security.Role.ORG_ADMIN) {
				throw new in.eko.eloka.shared.error.ForbiddenException("NOT_ALLOWED", "You are not allowed to do this.");
			}
			String mobile = in.eko.eloka.shared.crypto.Mobiles.normalize(agentMobile);
			if (users.findByMobile(caller.orgId(), mobile).isEmpty()) {
				return EpsEnvelope.ok().build();
			}
			var agent = network.findInScope(caller, mobile);
			return EpsResponse.Json.ok(Map.of("details", assembler.profileData(users.profile(caller.orgId(), agent.id()))));
		}
	}

	/** Client IP, used for watermarking uploads. */
	@Component
	static class ClientIp implements EpsHandler {

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.path("/transactions/getip"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.SESSION;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			return EpsResponse.Json.ok(Map.of("ip", call.request().clientIp()));
		}
	}
}
