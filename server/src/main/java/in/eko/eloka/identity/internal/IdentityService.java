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
	private final PinService pins;

	IdentityService(OtpService otps, SessionService sessions, UserDirectory users, AuditLog audit,
			IdentityProperties properties, PinService pins) {
		this.otps = otps;
		this.sessions = sessions;
		this.users = users;
		this.audit = audit;
		this.properties = properties;
		this.pins = pins;
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
	@Transactional
	public void sendEmailOtp(long orgId, String email, String clientIp) {
		otps.issueEmail(orgId, normalizeEmail(email), clientIp);
	}

	@Override
	@Transactional(noRollbackFor = RuntimeException.class)
	public boolean verifyEmailOtp(long orgId, String email, String code) {
		return otps.checkEmail(orgId, normalizeEmail(email), code) instanceof OtpService.Check.Valid;
	}

	static String normalizeEmail(String email) {
		String value = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
		if (!value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || value.length() > 254) {
			throw in.eko.eloka.shared.error.ValidationException.field("email", "Enter a valid email address");
		}
		return value;
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

	@Override
	@Transactional
	public OtpDispatch sendAgentConsentOtp(long orgId, String agentMobile, UUID assistantId, String clientIp) {
		String mobile = Mobiles.normalize(agentMobile);
		String code = otps.issueAgentConsent(orgId, mobile, assistantId, clientIp);
		return new OtpDispatch(mobile, properties.otpLength(), properties.exposeOtpInResponse() ? code : null);
	}

	@Override
	@Transactional(noRollbackFor = RuntimeException.class)
	public boolean verifyAgentConsentOtp(long orgId, String agentMobile, UUID assistantId, String code) {
		return otps.checkAgentConsent(orgId, Mobiles.normalize(agentMobile), assistantId, code) instanceof OtpService.Check.Valid;
	}

	@Override
	public boolean hasAgentConsent(long orgId, String agentMobile, UUID assistantId) {
		return otps.hasRecentConsent(orgId, Mobiles.normalize(agentMobile), assistantId, java.time.Duration.ofMinutes(30));
	}

	@Override
	public in.eko.eloka.identity.PinKey issuePinKey(UUID sessionId) {
		return pins.issue(sessionId);
	}

	@Override
	public void setTransactionPin(long orgId, UUID userId, UUID sessionId, String encodedPin, String encodedConfirm) {
		pins.setPin(orgId, userId, sessionId, encodedPin, encodedConfirm);
	}
}
