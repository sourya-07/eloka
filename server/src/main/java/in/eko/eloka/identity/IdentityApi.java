package in.eko.eloka.identity;

import java.util.Optional;
import java.util.UUID;

/** Public API of the identity module: OTP login and session lifecycle. */
public interface IdentityApi {

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
}
