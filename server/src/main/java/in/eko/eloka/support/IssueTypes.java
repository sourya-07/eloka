package in.eko.eloka.support;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The issues users can raise, by context: a transaction ({@code txTypeId > 0}), onboarding
 * ({@code -2}) or anything else ({@code -1}). Static reference data.
 */
@Component
public class IssueTypes {

	private static final IssueType.FileRequest SCREENSHOT = new IssueType.FileRequest("Screenshot", false);

	private static final List<IssueType> TRANSACTION = List.of(
			new IssueType("Amount debited but transaction failed", "We will check with the bank and refund if it failed.",
					4, "Transactions", 41, "Failed transaction", "3", "High", 0, List.of(), List.of(SCREENSHOT)),
			new IssueType("Refund not received", "Refunds usually reach the wallet within 3 working days.", 4, "Transactions",
					42, "Refund", "3", "High", 0, List.of(), List.of()),
			new IssueType("Customer has a complaint", null, 4, "Transactions", 43, "Customer complaint", "2", "Medium", 1,
					List.of(new IssueType.Input("Customer mobile", 11, false, 10)), List.of(SCREENSHOT)));

	private static final List<IssueType> ONBOARDING = List.of(
			new IssueType("KYC document not accepted", null, 5, "Onboarding", 51, "KYC", "2", "Medium", 1, List.of(),
					List.of(SCREENSHOT)),
			new IssueType("Cannot complete onboarding", null, 5, "Onboarding", 52, "Onboarding steps", "2", "Medium", 1,
					List.of(), List.of(SCREENSHOT)));

	private static final List<IssueType> GENERAL = List.of(
			new IssueType("Enable/Disable services", "Tell us which services to switch on or off.", 1, "Account & Services",
					11, "Services", "2", "Medium", 1, List.of(), List.of()),
			new IssueType("Wallet balance mismatch", null, 3, "Wallet", 31, "Balance", "2", "High", 1,
					List.of(new IssueType.Input("Amount", 11, true, 10)), List.of(SCREENSHOT)),
			new IssueType("Change registered mobile or email", null, 1, "Account & Services", 12, "Profile", "3", "Medium", 1,
					List.of(), List.of()),
			new IssueType("Something else", null, 9, "Others", 99, "Others", "3", "Low", 1, List.of(), List.of(SCREENSHOT)));

	public List<IssueType> forContext(int txTypeId) {
		if (txTypeId > 0) {
			return TRANSACTION;
		}
		return txTypeId == -2 ? ONBOARDING : GENERAL;
	}
}
