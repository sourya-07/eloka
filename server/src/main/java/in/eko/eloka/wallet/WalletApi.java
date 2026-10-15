package in.eko.eloka.wallet;

import java.util.List;
import java.util.UUID;

/** Public API of the wallet module. */
public interface WalletApi {

	List<WalletAccountView> accountsOf(long orgId, UUID userId);

	/** Opens the user's E-value account if missing. Idempotent. */
	WalletAccountView ensureEvalueAccount(long orgId, UUID userId, String userCode);

	/**
	 * Applies a posting to the user's E-value account.
	 *
	 * @return the account after posting
	 * @throws in.eko.eloka.shared.error.ConflictException on insufficient balance
	 */
	WalletAccountView post(LedgerPosting posting);

	/**
	 * E-value wallet statement, newest first.
	 *
	 * @param from inclusive date (Indian time), or null
	 * @param to inclusive date (Indian time), or null
	 */
	StatementPage statement(long orgId, UUID userId, java.time.LocalDate from, java.time.LocalDate to, int offset, int limit);
}
