package in.eko.eloka.wallet;

import java.math.BigDecimal;

/**
 * A wallet account as the client sees it in {@code account_detail.account_list}.
 *
 * @param id numeric account id (sent back by the client as {@code account_id})
 * @param typeId 1 = E-value wallet, 3 = TSP bank
 * @param accountNumber public account number
 * @param balance current balance
 */
public record WalletAccountView(long id, int typeId, String accountNumber, BigDecimal balance) {

	public static final int TYPE_EVALUE = 1;
}
