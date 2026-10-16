package in.eko.eloka.agentonboarding;

/** Settlement bank account entered in the bank step. */
public record BankAccountDetails(String bankCode, String bankId, String ifsc, String accountNumber) {
}
