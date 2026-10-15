package in.eko.eloka.identity.internal;

/** Strategy for producing OTP codes. */
interface OtpCodeGenerator {

	String generate(int length);
}
