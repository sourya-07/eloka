package in.eko.eloka.gateway.internal.handler;

import java.util.LinkedHashMap;
import java.util.Map;
import in.eko.eloka.history.TxnStatus;
import in.eko.eloka.history.TxnView;

/** Maps a transaction to the EPS history row the client renders. */
public final class TxnRows {

	private TxnRows() {
	}

	public static Map<String, Object> row(TxnView t) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("tid", Long.toString(t.tid()));
		row.put("tx_typeid", t.txTypeId());
		row.put("tx_name", t.txName());
		row.put("amount_dr", t.debit() ? t.amount() : 0);
		row.put("amount_cr", t.debit() ? 0 : t.amount());
		row.put("amount", t.amount());
		row.put("fee", t.fee());
		row.put("commission_earned", t.commission());
		row.put("status", statusLabel(t.status()));
		row.put("status_id", t.status().statusId());
		row.put("response_status_id", t.status() == TxnStatus.FAILED ? 1 : 0); // client hides amounts of failed rows
		row.put("datetime", EpsDates.format(t.occurredAt()));
		row.put("customer_name", t.customerName());
		row.put("customer_mobile", t.customerMobile());
		row.put("agent_code", t.userCode());
		t.details().forEach(row::putIfAbsent);
		return row;
	}

	private static String statusLabel(TxnStatus status) {
		return switch (status) {
			case SUCCESS -> "Success";
			case FAILED -> "Failed";
			case PENDING -> "Pending";
			case REFUNDED -> "Refunded";
		};
	}
}
