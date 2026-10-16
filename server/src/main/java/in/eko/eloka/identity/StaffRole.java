package in.eko.eloka.identity;

import java.util.Set;

/** Eko staff roles and the permissions they carry (docs/onboarding/P8_STAFF_REVIEW.md §1). */
public enum StaffRole {
	REVIEWER(Set.of(StaffPermission.ONBOARDING_READ, StaffPermission.ONBOARDING_REVIEW, StaffPermission.ONBOARDING_HOLD)),
	APPROVER(Set.of(StaffPermission.ONBOARDING_READ, StaffPermission.ONBOARDING_APPROVE, StaffPermission.ONBOARDING_REJECT,
			StaffPermission.ONBOARDING_HOLD, StaffPermission.PII_REVEAL)),
	AUDITOR(Set.of(StaffPermission.ONBOARDING_READ, StaffPermission.AUDIT_READ)),
	ADMIN(Set.of(StaffPermission.ONBOARDING_READ, StaffPermission.ONBOARDING_REVIEW, StaffPermission.ONBOARDING_HOLD,
			StaffPermission.ONBOARDING_APPROVE, StaffPermission.ONBOARDING_REJECT, StaffPermission.PII_REVEAL,
			StaffPermission.AUDIT_READ, StaffPermission.ONBOARDING_REASSIGN));

	private final Set<String> permissions;

	StaffRole(Set<String> permissions) {
		this.permissions = permissions;
	}

	public Set<String> permissions() {
		return permissions;
	}
}
