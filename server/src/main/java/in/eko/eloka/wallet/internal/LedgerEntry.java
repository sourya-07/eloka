package in.eko.eloka.wallet.internal;

import java.math.BigDecimal;
import java.time.Instant;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One immutable movement. {@code (account_id, reference)} is unique, which makes postings idempotent. */
@Entity
@Table(name = "ledger_entry", uniqueConstraints = @UniqueConstraint(columnNames = { "account_id", "reference" }))
class LedgerEntry extends BaseEntity {

	@Column(name = "account_id", nullable = false)
	private long accountId;

	@Column(nullable = false, length = 2)
	private String direction;

	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal amount;

	@Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
	private BigDecimal balanceAfter;

	@Column(nullable = false, length = 64)
	private String reference;

	@Column(length = 200)
	private String narration;

	@Column(name = "posted_at", nullable = false)
	private Instant postedAt;

	protected LedgerEntry() {
	}

	LedgerEntry(long accountId, boolean credit, BigDecimal amount, BigDecimal balanceAfter, String reference,
			String narration, Instant postedAt) {
		this.accountId = accountId;
		this.direction = credit ? "CR" : "DR";
		this.amount = amount;
		this.balanceAfter = balanceAfter;
		this.reference = reference;
		this.narration = narration;
		this.postedAt = postedAt;
	}
}
