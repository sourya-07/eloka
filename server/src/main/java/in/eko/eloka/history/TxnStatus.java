package in.eko.eloka.history;

/** Transaction state with the EPS status id the client shows. */
public enum TxnStatus {
	SUCCESS(0),
	FAILED(1),
	PENDING(2),
	REFUNDED(3);

	private final int statusId;

	TxnStatus(int statusId) {
		this.statusId = statusId;
	}

	public int statusId() {
		return statusId;
	}
}
