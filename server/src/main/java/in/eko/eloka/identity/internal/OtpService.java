package in.eko.eloka.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import in.eko.eloka.identity.IdentityProperties;
import in.eko.eloka.notification.SmsMessage;
import in.eko.eloka.notification.SmsSender;
import in.eko.eloka.shared.config.SafetyCheck;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.error.RateLimitedException;
import org.springframework.stereotype.Service;

/**
 * Issues and checks OTPs with rate limits (gap, per hour, per day, per IP). Codes are never
 * stored in clear: only {@code HMAC(pepper, salt ‖ code)}.
 */
@Service
class OtpService implements SafetyCheck {

	static final String DEVELOPMENT_PEPPER = "eloka-local-otp-pepper";

	/** Outcome of a check, used by {@link IdentityService}. */
	sealed interface Check {

		record Valid() implements Check {
		}

		record Wrong(int attemptsLeft) implements Check {
		}

		record NoLiveOtp() implements Check {
		}
	}

	private final OtpChallengeRepository challenges;
	private final OtpCodeGenerator generator;
	private final SmsSender sms;
	private final in.eko.eloka.notification.EmailSender emails;
	private final BlindIndex index;
	private final IdentityProperties properties;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	OtpService(OtpChallengeRepository challenges, OtpCodeGenerator generator, SmsSender sms,
			in.eko.eloka.notification.EmailSender emails, BlindIndex index, IdentityProperties properties, Clock clock) {
		this.challenges = challenges;
		this.generator = generator;
		this.sms = sms;
		this.emails = emails;
		this.index = index;
		this.properties = properties;
		this.clock = clock;
	}

	/** Login OTP by SMS. @return the generated code (callers decide whether it may be revealed) */
	String issue(long orgId, String mobile, String clientIp) {
		return issue(orgId, OtpChallenge.PURPOSE_LOGIN, index.hash("mobile", mobile), clientIp,
				code -> sms.send(new SmsMessage(mobile, "LOGIN_OTP",
						Map.of("otp", code, "minutes", Long.toString(properties.otpTtl().toMinutes())), "otp")));
	}

	Check check(long orgId, String mobile, String code) {
		return check(orgId, OtpChallenge.PURPOSE_LOGIN, index.hash("mobile", mobile), code);
	}

	/** Email verification OTP. */
	String issueEmail(long orgId, String email, String clientIp) {
		return issue(orgId, OtpChallenge.PURPOSE_EMAIL, index.hash("email", email), clientIp,
				code -> email(email, code));
	}

	Check checkEmail(long orgId, String email, String code) {
		return check(orgId, OtpChallenge.PURPOSE_EMAIL, index.hash("email", email), code);
	}

	/**
	 * OTP sent to an agent so a distributor/admin can onboard them. The target hash includes the
	 * assistant, so only the assistant who asked can use the code.
	 */
	String issueAgentConsent(long orgId, String agentMobile, java.util.UUID assistantId, String clientIp) {
		return issue(orgId, OtpChallenge.PURPOSE_CONSENT, consentTarget(assistantId, agentMobile), clientIp,
				code -> sms.send(new SmsMessage(agentMobile, "AGENT_CONSENT_OTP",
						Map.of("otp", code, "minutes", Long.toString(properties.otpTtl().toMinutes())), "otp")));
	}

	Check checkAgentConsent(long orgId, String agentMobile, java.util.UUID assistantId, String code) {
		return check(orgId, OtpChallenge.PURPOSE_CONSENT, consentTarget(assistantId, agentMobile), code);
	}

	boolean hasRecentConsent(long orgId, String agentMobile, java.util.UUID assistantId, java.time.Duration window) {
		Instant now = Instant.now(clock);
		return challenges.findFirstByOrgIdAndMobileHashAndPurposeOrderByCreatedAtDesc(orgId,
				consentTarget(assistantId, agentMobile), OtpChallenge.PURPOSE_CONSENT)
			.filter(challenge -> challenge.consumedWithin(now, window))
			.isPresent();
	}

	/** Local Admin applicant login OTP (platform org, purpose of its own). */
	String issueApplicant(long platformOrgId, String mobile, String clientIp) {
		return issue(platformOrgId, OtpChallenge.PURPOSE_APPLICANT, index.hash("mobile", mobile), clientIp,
				code -> sms.send(new SmsMessage(mobile, "APPLICANT_LOGIN_OTP",
						Map.of("otp", code, "minutes", Long.toString(properties.otpTtl().toMinutes())), "otp")));
	}

	Check checkApplicant(long platformOrgId, String mobile, String code) {
		return check(platformOrgId, OtpChallenge.PURPOSE_APPLICANT, index.hash("mobile", mobile), code);
	}

	/** Step-up OTP to the session's verified mobile; only that session can use it. */
	String issueReauth(long orgId, java.util.UUID sessionId, String mobile, String clientIp) {
		return issue(orgId, OtpChallenge.PURPOSE_REAUTH, reauthTarget(sessionId), clientIp,
				code -> sms.send(new SmsMessage(mobile, "REAUTH_OTP",
						Map.of("otp", code, "minutes", Long.toString(properties.otpTtl().toMinutes())), "otp")));
	}

	Check checkReauth(long orgId, java.util.UUID sessionId, String code) {
		return check(orgId, OtpChallenge.PURPOSE_REAUTH, reauthTarget(sessionId), code);
	}

	private byte[] reauthTarget(java.util.UUID sessionId) {
		return index.hash("reauth", sessionId.toString());
	}

	private byte[] consentTarget(java.util.UUID assistantId, String agentMobile) {
		return index.hash("consent", assistantId + ":" + agentMobile);
	}

	/**
	 * Creates a challenge for any target (mobile, email) and hands the code to {@code deliver}.
	 * Limits are counted per target across purposes.
	 */
	private String issue(long orgId, String purpose, byte[] targetHash, String clientIp,
			java.util.function.Consumer<String> deliver) {
		Instant now = Instant.now(clock);
		byte[] ipHash = clientIp == null ? null : index.hash("ip", clientIp);
		enforceLimits(orgId, purpose, targetHash, ipHash, now);
		String code = generator.generate(properties.otpLength());
		byte[] salt = new byte[16];
		random.nextBytes(salt);
		challenges.save(new OtpChallenge(orgId, targetHash, purpose, salt, hash(salt, code), ipHash,
				now.plus(properties.otpTtl()), properties.otpMaxAttempts()));
		deliver.accept(code);
		return code;
	}

	private Check check(long orgId, String purpose, byte[] targetHash, String code) {
		Instant now = Instant.now(clock);
		Optional<OtpChallenge> latest = challenges.findFirstByOrgIdAndMobileHashAndPurposeOrderByCreatedAtDesc(orgId,
				targetHash, purpose);
		if (latest.isEmpty() || !latest.get().isLive(now)) {
			return new Check.NoLiveOtp();
		}
		OtpChallenge challenge = latest.get();
		byte[] given = hash(challenge.getSalt(), code == null ? "" : code.trim());
		if (!MessageDigest.isEqual(given, challenge.getCodeHash())) {
			return new Check.Wrong(challenge.recordFailure());
		}
		challenge.consume(now);
		return new Check.Valid();
	}

	private void email(String to, String code) {
		emails.send(new in.eko.eloka.notification.EmailMessage(to, "EMAIL_OTP", "Your verification code",
				Map.of("otp", code, "minutes", Long.toString(properties.otpTtl().toMinutes())), "otp"));
	}

	@Override
	public List<String> productionViolations() {
		List<String> problems = new ArrayList<>();
		if (DEVELOPMENT_PEPPER.equals(properties.otpPepper())) {
			problems.add("eloka.identity.otp-pepper is the published development value");
		}
		if (properties.exposeOtpInResponse()) {
			problems.add("eloka.identity.expose-otp-in-response=true returns OTPs to the browser");
		}
		return problems;
	}

	private void enforceLimits(long orgId, String purpose, byte[] mobileHash, byte[] ipHash, Instant now) {
		challenges.findFirstByOrgIdAndMobileHashAndPurposeOrderByCreatedAtDesc(orgId, mobileHash, purpose)
			.map(OtpChallenge::getCreatedAt)
			.filter(last -> last.plus(properties.otpMinGap()).isAfter(now))
			.ifPresent(last -> {
				throw limited(Duration.between(now, last.plus(properties.otpMinGap())));
			});
		if (challenges.countByMobileHashAndCreatedAtAfter(mobileHash, now.minus(Duration.ofHours(1))) >= properties.otpMaxPerHour()) {
			throw limited(Duration.ofHours(1));
		}
		if (challenges.countByMobileHashAndCreatedAtAfter(mobileHash, now.minus(Duration.ofDays(1))) >= properties.otpMaxPerDay()) {
			throw limited(Duration.ofDays(1));
		}
		if (ipHash != null && challenges.countByClientIpHashAndCreatedAtAfter(ipHash, now.minus(Duration.ofHours(1)))
				>= properties.otpMaxPerIpPerHour()) {
			throw limited(Duration.ofHours(1));
		}
	}

	private static RateLimitedException limited(Duration retryAfter) {
		return new RateLimitedException("OTP_LIMIT_REACHED", "Too many OTP requests. Please try again later.", retryAfter);
	}

	private byte[] hash(byte[] salt, String code) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(properties.otpPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			mac.update(salt);
			return mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
