package in.eko.eloka.history;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * History filters (all optional). Dates are inclusive, Indian time.
 *
 * @param accountId only this wallet account
 * @param customerMobile exact customer mobile (matched through its blind index)
 */
public record HistoryFilter(Long accountId, Integer txTypeId, Long tid, String customerMobile, LocalDate from,
		LocalDate to, BigDecimal amount) {

	public static HistoryFilter none() {
		return new HistoryFilter(null, null, null, null, null, null, null);
	}
}
