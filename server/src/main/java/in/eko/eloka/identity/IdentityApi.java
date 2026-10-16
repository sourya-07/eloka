package in.eko.eloka.identity;

import java.util.Optional;
import java.util.UUID;

/** Public API of the identity module: OTP login and session lifecycle. */
public interface IdentityApi {

	/**
	 * Org id used for platform-level identities that belong to no tenant yet (Local Admin
	 * applicants). No tenant has this id; identity tables have no foreign key to {@code org}.
	 */
	long PLATFORM_ORG_ID = 0L;

	/**
	 * Sends a login OTP.
	 *
	 * @throws in.eko.eloka.shared.error.RateLimitedException when limits are hit
	 */
	OtpDispatch sendLoginOtp(long orgId, String mobile, String clientIp);

	LoginResult verifyLoginOtp(long orgId, String mobile, String code);

	/** Rotates both tokens. Empty when the refresh token is unknown, expired or reused. */
	Optional<TokenPair> refresh(String refreshToken);

	/** Issues new tokens for a live session (used by refresh-profile). */
	TokenPair rotate(UUID sessionId);

	/** Ends the session that owns this access or refresh token. Unknown tokens are ignored. */
	void revoke(String token, String reason);

	/** Sends a code to verify an email address the caller wants to use. */
	void sendEmailOtp(long orgId, String email, String clientIp);

	/** @return true when the code matches the latest live code sent to this email */
	boolean verifyEmailOtp(long orgId, String email, String code);

	/** Links a pre-registration session to the account created during self-onboarding. */
	void attachUser(UUID sessionId, UUID userId);

	/** Sends the agent an OTP so {@code assistantId} may onboard them. */
	OtpDispatch sendAgentConsentOtp(long orgId, String agentMobile, UUID assistantId, String clientIp);

	boolean verifyAgentConsentOtp(long orgId, String agentMobile, UUID assistantId, String code);

	/** True when the agent confirmed {@code assistantId}'s OTP within the last 30 minutes. */
	boolean hasAgentConsent(long orgId, String agentMobile, UUID assistantId);

	/** A fresh one-time PinTwin key for this session (valid 10 minutes). */
	PinKey issuePinKey(UUID sessionId);

	/**
	 * Sets the user's secret transaction PIN from two PinTwin-encoded entries ({@code "<code>|<keyId>"}).
	 *
	 * @throws in.eko.eloka.shared.error.ValidationException when the key is unknown/expired, the entries
	 *         differ, or the PIN is too weak
	 */
	void setTransactionPin(long orgId, UUID userId, UUID sessionId, String encodedPin, String encodedConfirm);

	/** Sends a Local Admin applicant their login OTP (same limits as agent login). */
	OtpDispatch sendApplicantOtp(String mobile, String clientIp);

	/** Checks an applicant OTP. Opening the session is a separate step ({@link #openApplicantSession}). */
	ApplicantOtpResult verifyApplicantOtp(String mobile, String code);

	/** Opens an {@code LA_APPLICANT} session (valid only on {@code /v1/onboarding/**}). */
	TokenPair openApplicantSession(UUID applicantId, String mobile);

	/** Sends a step-up OTP to the verified mobile of this session. */
	OtpDispatch sendReauthOtp(long orgId, UUID sessionId, String mobile, String clientIp);

	/** @return a single-use re-auth token (valid 5 minutes, this session only) when the code is right */
	Optional<String> verifyReauthOtp(long orgId, UUID sessionId, String code);

	/** Uses a re-auth token. @return false when unknown, expired, used, or for another session */
	boolean consumeReauthToken(UUID sessionId, String token);
}
