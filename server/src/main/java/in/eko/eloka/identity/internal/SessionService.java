package in.eko.eloka.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import in.eko.eloka.identity.IdentityProperties;
import in.eko.eloka.identity.TokenPair;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Tokens;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.CallerResolver;
import in.eko.eloka.shared.security.Role;
import in.eko.eloka.shared.security.SessionKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, resolves, rotates and revokes sessions. Implements bearer-token authentication. */
@Service
class SessionService implements CallerResolver {

	static final String ACCESS_PREFIX = "elk_at_";
	static final String REFRESH_PREFIX = "elk_rt_";

	private final AuthSessionRepository sessions;
	private final UserDirectory users;
	private final FieldCipher cipher;
	private final IdentityProperties properties;
	private final Clock clock;

	SessionService(AuthSessionRepository sessions, UserDirectory users, FieldCipher cipher,
			IdentityProperties properties, Clock clock) {
		this.sessions = sessions;
		this.users = users;
		this.cipher = cipher;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	TokenPair open(SessionKind kind, long orgId, UUID userId, String mobile) {
		Instant now = Instant.now(clock);
		String access = Tokens.random(ACCESS_PREFIX);
		String refresh = Tokens.random(REFRESH_PREFIX);
		AuthSession session = sessions.save(new AuthSession(kind, orgId, userId, cipher.encrypt(mobile),
				Tokens.sha256(access), Tokens.sha256(refresh), now, properties.sessionIdleTimeout(),
				properties.sessionAbsoluteTtl()));
		return new TokenPair(access, refresh, session.remaining(now));
	}

	@Override
	@Transactional
	public Optional<Caller> resolve(String bearerToken) {
		if (!bearerToken.startsWith(ACCESS_PREFIX)) {
			return Optional.empty();
		}
		Instant now = Instant.now(clock);
		return sessions.findByAccessTokenHash(Tokens.sha256(bearerToken))
			.filter(session -> session.isActive(now))
			.flatMap(session -> toCaller(session, now));
	}

	@Transactional
	Optional<TokenPair> refresh(String refreshToken) {
		if (refreshToken == null || !refreshToken.startsWith(REFRESH_PREFIX)) {
			return Optional.empty();
		}
		Instant now = Instant.now(clock);
		byte[] hash = Tokens.sha256(refreshToken);
		Optional<AuthSession> current = sessions.findByRefreshTokenHash(hash);
		if (current.isEmpty()) {
			// A rotated-away refresh token being used again means it leaked: end that session.
			sessions.findByPreviousRefreshHash(hash).ifPresent(session -> session.revoke(now, "REFRESH_REUSE"));
			return Optional.empty();
		}
		AuthSession session = current.get();
		if (!session.isActive(now)) {
			return Optional.empty();
		}
		return Optional.of(rotate(session, now));
	}

	@Transactional
	TokenPair rotate(UUID sessionId) {
		AuthSession session = sessions.findById(sessionId)
			.orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Session not found"));
		return rotate(session, Instant.now(clock));
	}

	@Transactional
	void revoke(String token, String reason) {
		if (token == null) {
			return;
		}
		Instant now = Instant.now(clock);
		byte[] hash = Tokens.sha256(token);
		Optional<AuthSession> session = token.startsWith(REFRESH_PREFIX) ? sessions.findByRefreshTokenHash(hash)
				: sessions.findByAccessTokenHash(hash);
		session.ifPresent(s -> s.revoke(now, reason));
	}

	@Transactional
	void attachUser(UUID sessionId, UUID userId) {
		sessions.findById(sessionId).ifPresent(session -> session.attachUser(userId));
	}

	private TokenPair rotate(AuthSession session, Instant now) {
		String access = Tokens.random(ACCESS_PREFIX);
		String refresh = Tokens.random(REFRESH_PREFIX);
		session.rotate(Tokens.sha256(access), Tokens.sha256(refresh), now, properties.sessionIdleTimeout());
		return new TokenPair(access, refresh, session.remaining(now));
	}

	private Optional<Caller> toCaller(AuthSession session, Instant now) {
		String mobile = cipher.decrypt(session.getMobileEnc());
		if (session.getKind() == SessionKind.PRE_REGISTRATION) {
			session.touch(now, properties.sessionIdleTimeout());
			return Optional.of(new Caller(session.getId(), session.getKind(), session.getOrgId(), null, null, mobile,
					Role.PRE_REGISTRATION, Set.of()));
		}
		UserView user = users.getById(session.getOrgId(), session.getUserId());
		if (!user.isActive()) {
			session.revoke(now, "USER_NOT_ACTIVE");
			return Optional.empty();
		}
		session.touch(now, properties.sessionIdleTimeout());
		return Optional.of(new Caller(session.getId(), session.getKind(), session.getOrgId(), user.id(), user.userCode(),
				mobile, user.role(), Set.of()));
	}
}
