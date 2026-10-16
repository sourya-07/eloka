package in.eko.eloka.identity.internal;

import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** The user's secret transaction PIN, stored only as a BCrypt hash. */
@Entity
@Table(name = "transaction_pin")
class TransactionPin extends BaseEntity {

	@Column(name = "user_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(name = "pin_hash", nullable = false, length = 72)
	private String pinHash;

	protected TransactionPin() {
	}

	TransactionPin(UUID userId, String pinHash) {
		this.userId = userId;
		this.pinHash = pinHash;
	}

	void replace(String newHash) {
		this.pinHash = newHash;
	}
}
