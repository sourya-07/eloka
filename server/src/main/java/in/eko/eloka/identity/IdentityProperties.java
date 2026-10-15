package in.eko.eloka.identity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login and session rules under {@code eloka.identity}.
 *
 * @param otpLength digits in an OTP
 * @param otpTtl how long an OTP is valid
 * @param otpMaxAttempts wrong guesses allowed per OTP
 * @param otpMinGap minimum time between two OTPs to the same mobile
 * @param otpMaxPerHour OTPs per mobile per hour
 * @param otpMaxPerDay OTPs per mobile per day
 * @param otpMaxPerIpPerHour OTPs per client IP per hour
 * @param otpPepper secret mixed into stored OTP hashes
 * @param fixedOtp local/test only: every OTP equals this value
 * @param exposeOtpInResponse local only: return the OTP in the send-OTP response ("Demo OTP" toast)
 * @param sessionIdleTimeout session ends after this much inactivity
 * @param sessionAbsoluteTtl session ends this long after login, whatever happens
 */
@ConfigurationProperties("eloka.identity")
public record IdentityProperties(int otpLength, Duration otpTtl, int otpMaxAttempts, Duration otpMinGap,
		int otpMaxPerHour, int otpMaxPerDay, int otpMaxPerIpPerHour, String otpPepper, String fixedOtp,
		boolean exposeOtpInResponse, Duration sessionIdleTimeout, Duration sessionAbsoluteTtl) {

	public IdentityProperties {
		otpLength = otpLength == 0 ? 6 : otpLength;
		otpTtl = otpTtl == null ? Duration.ofMinutes(5) : otpTtl;
		otpMaxAttempts = otpMaxAttempts == 0 ? 5 : otpMaxAttempts;
		otpMinGap = otpMinGap == null ? Duration.ofSeconds(30) : otpMinGap;
		otpMaxPerHour = otpMaxPerHour == 0 ? 5 : otpMaxPerHour;
		otpMaxPerDay = otpMaxPerDay == 0 ? 10 : otpMaxPerDay;
		otpMaxPerIpPerHour = otpMaxPerIpPerHour == 0 ? 10 : otpMaxPerIpPerHour;
		sessionIdleTimeout = sessionIdleTimeout == null ? Duration.ofHours(2) : sessionIdleTimeout;
		sessionAbsoluteTtl = sessionAbsoluteTtl == null ? Duration.ofHours(12) : sessionAbsoluteTtl;
	}
}
