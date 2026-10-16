package in.eko.eloka.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Staff sign-in ({@code eloka.staff}).
 *
 * @param otpLoginEnabled local/test only: staff may log in with an OTP to their mobile
 * @param googleClientId OAuth client id of the staff console; empty = Google sign-in not configured
 * @param googleDomain Google Workspace domain staff accounts must belong to (e.g. {@code eko.co.in})
 */
@ConfigurationProperties("eloka.staff")
public record StaffProperties(boolean otpLoginEnabled, String googleClientId, String googleDomain) {
}
