package in.eko.eloka.history.internal;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Transaction ids shown to users ("TID: 3561381530"): 10 digits, time-ordered prefix plus
 * random suffix so they are unique without a shared counter. Uniqueness is still enforced by
 * the database.
 */
@Component
class TidAllocator {

	private final SecureRandom random = new SecureRandom();

	long next(long epochSeconds) {
		long base = (epochSeconds % 1_000_000L) * 10_000L;
		return 3_000_000_000L + base + random.nextInt(10_000);
	}
}
