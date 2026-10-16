package in.eko.eloka.identity;

/** Outcome of a staff sign-in (Google or, locally, OTP). */
public sealed interface StaffLoginResult {

	record Success(TokenPair tokens, StaffView staff) implements StaffLoginResult {
	}

	record WrongCode(int attemptsLeft) implements StaffLoginResult {
	}

	record Expired() implements StaffLoginResult {
	}

	/** Not a staff member, disabled, wrong Google domain or unverified email (same answer for all). */
	record NotAllowed() implements StaffLoginResult {
	}
}
