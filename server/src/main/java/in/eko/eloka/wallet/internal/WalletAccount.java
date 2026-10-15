package in.eko.eloka.wallet.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A user's wallet. The balance only changes through {@link #apply}, which never goes negative. */
@Entity
@Table(name = "wallet_account")
class WalletAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(name = "type_id", nullable = false)
	private int typeId;

	@Column(name = "account_number", nullable = false, unique = true, length = 30)
	private String accountNumber;

	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal balance;

	@Version
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected WalletAccount() {
	}

	WalletAccount(long orgId, UUID userId, int typeId, String accountNumber) {
		this.orgId = orgId;
		this.userId = userId;
		this.typeId = typeId;
		this.accountNumber = accountNumber;
		this.balance = BigDecimal.ZERO.setScale(2);
	}

	/** @return balance after the movement */
	BigDecimal apply(boolean credit, BigDecimal amount) {
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("Amount must be positive");
		}
		BigDecimal next = credit ? balance.add(amount) : balance.subtract(amount);
		if (next.signum() < 0) {
			throw new ConflictException("INSUFFICIENT_BALANCE", "Insufficient wallet balance");
		}
		balance = next.setScale(2);
		return balance;
	}

	Long getId() {
		return id;
	}

	int getTypeId() {
		return typeId;
	}

	String getAccountNumber() {
		return accountNumber;
	}

	BigDecimal getBalance() {
		return balance;
	}
}
