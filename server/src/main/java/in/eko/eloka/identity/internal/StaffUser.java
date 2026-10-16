package in.eko.eloka.identity.internal;

import java.util.EnumSet;
import java.util.Set;
import in.eko.eloka.identity.StaffRole;
import in.eko.eloka.identity.StaffView;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** An Eko staff member who reviews Local Admin applications. Identified by their Google email. */
@Entity
@Table(name = "staff_user")
class StaffUser extends BaseEntity {

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	/** Only for local/test OTP sign-in. */
	@Column(name = "mobile_hash", unique = true, columnDefinition = "BINARY(32)")
	private byte[] mobileHash;

	@Column(nullable = false, length = 20)
	private String status;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "staff_role_grant", joinColumns = @JoinColumn(name = "staff_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Set<StaffRole> roles = EnumSet.noneOf(StaffRole.class);

	protected StaffUser() {
	}

	StaffUser(String email, String displayName) {
		this.email = email;
		this.displayName = displayName;
		this.status = "ACTIVE";
	}

	/** A blank name, no mobile or no roles keep what the account has (e.g. only disabling it). */
	void update(String newDisplayName, byte[] newMobileHash, Set<StaffRole> newRoles, boolean active) {
		if (newDisplayName != null && !newDisplayName.isBlank()) {
			this.displayName = newDisplayName;
		}
		if (newMobileHash != null) {
			this.mobileHash = newMobileHash;
		}
		if (!newRoles.isEmpty()) {
			this.roles.clear();
			this.roles.addAll(newRoles);
		}
		this.status = active ? "ACTIVE" : "DISABLED";
	}

	boolean isActive() {
		return "ACTIVE".equals(status);
	}

	Set<String> permissions() {
		Set<String> all = new java.util.HashSet<>();
		roles.forEach(role -> all.addAll(role.permissions()));
		return all;
	}

	StaffView view() {
		return new StaffView(getId(), email, displayName, roles, isActive());
	}

	String getEmail() {
		return email;
	}
}
