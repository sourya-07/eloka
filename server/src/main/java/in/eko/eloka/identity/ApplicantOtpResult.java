package in.eko.eloka.identity;

/** Outcome of checking a Local Admin applicant's OTP. */
public sealed interface ApplicantOtpResult {

	/** OTP accepted; {@code mobile} is the normalised, now verified number. */
	record Verified(String mobile) implements ApplicantOtpResult {
	}

	/** Wrong code. {@code attemptsLeft} is 0 when this OTP is now locked. */
	record WrongCode(int attemptsLeft) implements ApplicantOtpResult {
	}

	/** No live OTP for this mobile (expired, used, locked or never sent). */
	record Expired() implements ApplicantOtpResult {
	}
}
