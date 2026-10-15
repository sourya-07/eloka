package in.eko.eloka.identity;

/**
 * Result of sending an OTP.
 *
 * @param mobile normalised mobile the OTP went to
 * @param length digits in the OTP
 * @param revealedCode the OTP itself, only when local "expose in response" is on; otherwise null
 */
public record OtpDispatch(String mobile, int length, String revealedCode) {
}
