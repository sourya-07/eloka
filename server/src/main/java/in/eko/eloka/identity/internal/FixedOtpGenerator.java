package in.eko.eloka.identity.internal;

import in.eko.eloka.identity.IdentityProperties;
import in.eko.eloka.shared.config.UnsafeInProduction;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local and test runs only: every OTP is {@code eloka.identity.fixed-otp} (default 123456),
 * so dummy logins work without SMS. Production refuses to start with this bean present.
 */
@Component
@Primary
@Profile({ "local", "test" })
class FixedOtpGenerator implements OtpCodeGenerator, UnsafeInProduction {

	private final String code;

	FixedOtpGenerator(IdentityProperties properties) {
		this.code = properties.fixedOtp() == null || properties.fixedOtp().isBlank() ? "123456" : properties.fixedOtp();
	}

	@Override
	public String generate(int length) {
		return code;
	}

	@Override
	public String unsafeReason() {
		return "FixedOtpGenerator is active: every OTP is " + "*".repeat(code.length());
	}
}
