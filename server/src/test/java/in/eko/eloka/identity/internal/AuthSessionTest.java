package in.eko.eloka.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.security.SessionKind;
import org.junit.jupiter.api.Test;

class AuthSessionTest {

	private static final Duration IDLE = Duration.ofHours(2);
	private static final Duration ABSOLUTE = Duration.ofHours(12);
	private static final Duration ACCESS = Duration.ofMinutes(30);
	private static final Instant T0 = Instant.parse("2026-10-16T10:00:00Z");

	private AuthSession session() {
		return new AuthSession(SessionKind.USER, 1, UUID.randomUUID(), new byte[] { 1 }, new byte[32], new byte[32], T0,
				IDLE, ABSOLUTE, ACCESS);
	}

	@Test
	void accessTokenExpiresBeforeTheSession() {
		AuthSession session = session();
		Instant later = T0.plus(Duration.ofMinutes(31));
		assertThat(session.acceptsAccessToken(T0.plus(Duration.ofMinutes(29)))).isTrue();
		assertThat(session.acceptsAccessToken(later)).isFalse();
		assertThat(session.isActive(later)).isTrue();
	}

	@Test
	void rotationIssuesAFreshAccessWindow() {
		AuthSession session = session();
		Instant later = T0.plus(Duration.ofMinutes(31));
		session.rotate(new byte[32], new byte[32], later, IDLE, ACCESS);
		assertThat(session.acceptsAccessToken(later.plus(Duration.ofMinutes(29)))).isTrue();
		assertThat(session.accessRemaining(later)).isEqualTo(ACCESS);
	}

	@Test
	void accessNeverOutlivesTheAbsoluteLimit() {
		AuthSession session = session();
		Instant nearEnd = T0.plus(ABSOLUTE).minus(Duration.ofMinutes(10));
		session.rotate(new byte[32], new byte[32], nearEnd, IDLE, ACCESS);
		assertThat(session.accessRemaining(nearEnd)).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void revokedSessionRejectsItsAccessToken() {
		AuthSession session = session();
		session.revoke(T0, "LOGOUT");
		assertThat(session.acceptsAccessToken(T0.plusSeconds(1))).isFalse();
	}
}
