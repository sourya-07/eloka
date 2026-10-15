package in.eko.eloka.identity.internal;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Real OTPs: uniformly random digits from a cryptographic RNG. */
@Component
class SecureRandomOtpGenerator implements OtpCodeGenerator {

	private final SecureRandom random = new SecureRandom();

	@Override
	public String generate(int length) {
		StringBuilder code = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			code.append(random.nextInt(10));
		}
		return code.toString();
	}
}
