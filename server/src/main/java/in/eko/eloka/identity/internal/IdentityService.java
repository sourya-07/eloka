package in.eko.eloka.identity.internal;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.identity.ApplicantOtpResult;
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
	private final ReauthGrantRepository reauthGrants;
	private final java.time.Clock clock;

	IdentityService(OtpService otps, SessionService sessions, UserDirectory users, AuditLog audit,
			IdentityProperties properties, PinService pins, ReauthGrantRepository reauthGrants, java.time.Clock clock) {
		this.otps = otps;
		this.sessions = sessions;
		this.users = users;
		this.audit = audit;
		this.properties = properties;
		this.pins = pins;
		this.reauthGrants = reauthGrants;
		this.clock = clock;
	}

	/** How long a step-up (re-auth) token stays usable. */
	static final java.time.Duration REAUTH_TTL = java.time.Duration.ofMinutes(5);

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

	@Override
	@Transactional
	public OtpDispatch sendApplicantOtp(String mobile, String clientIp) {
		String normalized = Mobiles.normalize(mobile);
		String code = otps.issueApplicant(PLATFORM_ORG_ID, normalized, clientIp);
		return new OtpDispatch(normalized, properties.otpLength(), properties.exposeOtpInResponse() ? code : null);
	}

	@Override
	@Transactional(noRollbackFor = RuntimeException.class)
	public ApplicantOtpResult verifyApplicantOtp(String mobile, String code) {
		String normalized = Mobiles.normalize(mobile);
		OtpService.Check check = otps.checkApplicant(PLATFORM_ORG_ID, normalized, code);
		if (check instanceof OtpService.Check.NoLiveOtp) {
			return new ApplicantOtpResult.Expired();
		}
		if (check instanceof OtpService.Check.Wrong wrong) {
			audit.record(new AuditEntry("APPLICANT_OTP_REJECTED", "ANONYMOUS", null, PLATFORM_ORG_ID, "APPLICANT", null,
					Map.of("attempts_left", Integer.toString(wrong.attemptsLeft()))));
			return new ApplicantOtpResult.WrongCode(wrong.attemptsLeft());
		}
		return new ApplicantOtpResult.Verified(normalized);
	}

	@Override
	@Transactional
	public TokenPair openApplicantSession(UUID applicantId, String mobile) {
		TokenPair tokens = sessions.open(SessionKind.LA_APPLICANT, PLATFORM_ORG_ID, applicantId, Mobiles.normalize(mobile));
		audit.record(new AuditEntry("APPLICANT_LOGIN_SUCCEEDED", "APPLICANT", null, PLATFORM_ORG_ID, "APPLICANT",
				applicantId.toString(), Map.of()));
		return tokens;
	}

	@Override
	@Transactional
	public OtpDispatch sendReauthOtp(long orgId, UUID sessionId, String mobile, String clientIp) {
		String code = otps.issueReauth(orgId, sessionId, mobile, clientIp);
		return new OtpDispatch(mobile, properties.otpLength(), properties.exposeOtpInResponse() ? code : null);
	}

	@Override
	@Transactional(noRollbackFor = RuntimeException.class)
	public Optional<String> verifyReauthOtp(long orgId, UUID sessionId, String code) {
		if (!(otps.checkReauth(orgId, sessionId, code) instanceof OtpService.Check.Valid)) {
			return Optional.empty();
		}
		String token = in.eko.eloka.shared.crypto.Tokens.random("elk_ra_");
		reauthGrants.save(new ReauthGrant(sessionId, in.eko.eloka.shared.crypto.Tokens.sha256(token),
				java.time.Instant.now(clock).plus(REAUTH_TTL)));
		return Optional.of(token);
	}

	@Override
	@Transactional
	public boolean consumeReauthToken(UUID sessionId, String token) {
		if (token == null || !token.startsWith("elk_ra_")) {
			return false;
		}
		return reauthGrants.findByTokenHash(in.eko.eloka.shared.crypto.Tokens.sha256(token))
			.map(grant -> grant.use(sessionId, java.time.Instant.now(clock)))
			.orElse(false);
	}
}
