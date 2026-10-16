package in.eko.eloka.integration;

/** Penny-drop / name lookup of a bank account. */
public interface BankAccountVerifier {

	/**
	 * @param hintName the expected holder name; real providers ignore it, fakes echo it
	 * @throws in.eko.eloka.shared.error.ConflictException {@code PROVIDER_NOT_CONFIGURED} outside local/test
	 */
	BankCheck verify(String ifsc, String accountNumber, String hintName);

	/**
	 * @param beneficiaryName holder name returned by the bank (when valid)
	 * @param failureCode e.g. {@code BANK_ACCOUNT_INVALID} (when not valid)
	 */
	record BankCheck(boolean valid, String beneficiaryName, String failureCode) {
	}
}
