package in.eko.eloka.identity;

/** Outcome of checking a login OTP. */
public sealed interface LoginResult {

	/** OTP accepted. {@code registered} is false when the mobile has no account yet (self-onboarding). */
	record Success(TokenPair tokens, boolean registered) implements LoginResult {
	}

	record WrongCode(int attemptsLeft) implements LoginResult {
	}

	/** No live OTP for this mobile (expired, used, or never sent). */
	record Expired() implements LoginResult {
	}

	/** The account exists but may not log in. */
	record AccountInactive() implements LoginResult {
	}
}
