package in.eko.eloka.history.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.history.TxnStatus;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** One transaction as shown in history. Immutable once recorded. */
@Entity
@Table(name = "txn_record")
class TxnRecord extends BaseEntity {

	@Column(nullable = false, unique = true)
	private long tid;

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(name = "user_code", nullable = false, length = 20)
	private String userCode;

	@Column(name = "account_id")
	private Long accountId;

	@Column(name = "tx_typeid", nullable = false)
	private int txTypeId;

	@Column(name = "tx_name", nullable = false, length = 80)
	private String txName;

	@Column(nullable = false)
	private boolean debit;

	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal fee;

	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal commission;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TxnStatus status;

	@Column(name = "customer_name", length = 150)
	private String customerName;

	@Column(name = "customer_mobile_enc", columnDefinition = "VARBINARY(128)")
	private byte[] customerMobileEnc;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(length = 4000)
	private String details;

	protected TxnRecord() {
	}

	TxnRecord(long tid, long orgId, UUID userId, String userCode, Long accountId, int txTypeId, String txName,
			boolean debit, BigDecimal amount, BigDecimal fee, BigDecimal commission, TxnStatus status,
			String customerName, byte[] customerMobileEnc, Instant occurredAt, String details) {
		this.tid = tid;
		this.orgId = orgId;
		this.userId = userId;
		this.userCode = userCode;
		this.accountId = accountId;
		this.txTypeId = txTypeId;
		this.txName = txName;
		this.debit = debit;
		this.amount = amount;
		this.fee = fee;
		this.commission = commission;
		this.status = status;
		this.customerName = customerName;
		this.customerMobileEnc = customerMobileEnc;
		this.occurredAt = occurredAt;
		this.details = details;
	}

	long getTid() { return tid; }
	long getOrgId() { return orgId; }
	UUID getUserId() { return userId; }
	String getUserCode() { return userCode; }
	Long getAccountId() { return accountId; }
	int getTxTypeId() { return txTypeId; }
	String getTxName() { return txName; }
	boolean isDebit() { return debit; }
	BigDecimal getAmount() { return amount; }
	BigDecimal getFee() { return fee; }
	BigDecimal getCommission() { return commission; }
	TxnStatus getStatus() { return status; }
	String getCustomerName() { return customerName; }
	byte[] getCustomerMobileEnc() { return customerMobileEnc; }
	Instant getOccurredAt() { return occurredAt; }
	String getDetails() { return details; }
}
