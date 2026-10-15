package in.eko.eloka.identity.internal;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.IdentityProperties;
import in.eko.eloka.identity.LoginResult;
import in.eko.eloka.identity.OtpDispatch;
import in.eko.eloka.identity.TokenPair;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.security.SessionKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login use cases: send OTP, verify OTP and open a session, token lifecycle. */
@Service
class IdentityService implements IdentityApi {

	private final OtpService otps;
	private final SessionService sessions;
	private final UserDirectory users;
	private final AuditLog audit;
	private final IdentityProperties properties;

	IdentityService(OtpService otps, SessionService sessions, UserDirectory users, AuditLog audit,
			IdentityProperties properties) {
		this.otps = otps;
		this.sessions = sessions;
		this.users = users;
		this.audit = audit;
		this.properties = properties;
	}

	@Override
	@Transactional
	public OtpDispatch sendLoginOtp(long orgId, String mobile, String clientIp) {
		String normalized = Mobiles.normalize(mobile);
		String code = otps.issue(orgId, normalized, clientIp);
		return new OtpDispatch(normalized, properties.otpLength(), properties.exposeOtpInResponse() ? code : null);
	}

	@Override
	@Transactional(noRollbackFor = RuntimeException.class)
	public LoginResult verifyLoginOtp(long orgId, String mobile, String code) {
		String normalized = Mobiles.normalize(mobile);
		OtpService.Check check = otps.check(orgId, normalized, code);
		if (check instanceof OtpService.Check.NoLiveOtp) {
			return new LoginResult.Expired();
		}
		if (check instanceof OtpService.Check.Wrong wrong) {
			audit.record(new AuditEntry("LOGIN_OTP_REJECTED", "ANONYMOUS", null, orgId, "SESSION", null,
					Map.of("attempts_left", Integer.toString(wrong.attemptsLeft()))));
			return new LoginResult.WrongCode(wrong.attemptsLeft());
		}

		Optional<UserView> user = users.findByMobile(orgId, normalized);
		if (user.isPresent() && !user.get().isActive()) {
			audit.record(new AuditEntry("LOGIN_BLOCKED_INACTIVE", "USER", user.get().userCode(), orgId, "USER",
					user.get().userCode(), Map.of()));
			return new LoginResult.AccountInactive();
		}
		SessionKind kind = user.isPresent() ? SessionKind.USER : SessionKind.PRE_REGISTRATION;
		TokenPair tokens = sessions.open(kind, orgId, user.map(UserView::id).orElse(null), normalized);
		audit.record(new AuditEntry("LOGIN_SUCCEEDED", user.isPresent() ? "USER" : "ANONYMOUS",
				user.map(UserView::userCode).orElse(null), orgId, "SESSION", null, Map.of("kind", kind.name())));
		return new LoginResult.Success(tokens, user.isPresent());
	}

	@Override
	public Optional<TokenPair> refresh(String refreshToken) {
		return sessions.refresh(refreshToken);
	}

	@Override
	public TokenPair rotate(UUID sessionId) {
		return sessions.rotate(sessionId);
	}

	@Override
	public void revoke(String token, String reason) {
		sessions.revoke(token, reason);
	}

	@Override
	public void attachUser(UUID sessionId, UUID userId) {
		sessions.attachUser(sessionId, userId);
	}
}
