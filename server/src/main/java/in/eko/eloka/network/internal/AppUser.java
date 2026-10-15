package in.eko.eloka.network.internal;

import java.time.LocalDate;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A user of one tenant. Mobile and email are stored encrypted, with a keyed hash for
 * lookups and a masked copy for display.
 */
@Entity
@Table(name = "app_user")
class AppUser extends BaseEntity {

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "user_code", nullable = false, unique = true, length = 20)
	private String userCode;

	@Column(name = "user_type", nullable = false)
	private int userType;

	@Column(name = "org_admin", nullable = false)
	private boolean orgAdmin;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(name = "mobile_enc", nullable = false, columnDefinition = "VARBINARY(128)")
	private byte[] mobileEnc;

	@Column(name = "mobile_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] mobileHash;

	@Column(name = "mobile_masked", nullable = false, length = 16)
	private String mobileMasked;

	@Column(name = "email_enc", columnDefinition = "VARBINARY(512)")
	private byte[] emailEnc;

	@Column(name = "date_of_joining", nullable = false)
	private LocalDate dateOfJoining;

	@Column(nullable = false)
	private boolean demo;

	@Column(name = "parent_user_id", columnDefinition = "BINARY(16)")
	private java.util.UUID parentUserId;

	/** Materialised path of ancestor ids, e.g. {@code /<parent>/<self>/}; for subtree queries. */
	@Column(name = "hierarchy_path", length = 800)
	private String hierarchyPath;

	protected AppUser() {
	}

	AppUser(long orgId, String userCode, int userType, boolean orgAdmin, UserStatus status, String name,
			byte[] mobileEnc, byte[] mobileHash, String mobileMasked, byte[] emailEnc, LocalDate dateOfJoining,
			boolean demo) {
		this.orgId = orgId;
		this.userCode = userCode;
		this.userType = userType;
		this.orgAdmin = orgAdmin;
		this.status = status;
		this.name = name;
		this.mobileEnc = mobileEnc;
		this.mobileHash = mobileHash;
		this.mobileMasked = mobileMasked;
		this.emailEnc = emailEnc;
		this.dateOfJoining = dateOfJoining;
		this.demo = demo;
	}

	long getOrgId() {
		return orgId;
	}

	String getUserCode() {
		return userCode;
	}

	int getUserType() {
		return userType;
	}

	boolean isOrgAdmin() {
		return orgAdmin;
	}

	UserStatus getStatus() {
		return status;
	}

	String getName() {
		return name;
	}

	byte[] getMobileEnc() {
		return mobileEnc;
	}

	byte[] getEmailEnc() {
		return emailEnc;
	}

	LocalDate getDateOfJoining() {
		return dateOfJoining;
	}

	boolean isDemo() {
		return demo;
	}

	java.util.UUID getParentUserId() {
		return parentUserId;
	}

	String getHierarchyPath() {
		return hierarchyPath;
	}

	/** Places the user in the network; called once after the id exists. */
	void placeUnder(AppUser parent) {
		this.parentUserId = parent == null ? null : parent.getId();
		String prefix = parent == null ? "/" : parent.getHierarchyPath();
		this.hierarchyPath = prefix + getId() + "/";
	}
}
