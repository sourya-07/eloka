package in.eko.eloka.identity;

/** Permission names carried by staff sessions ({@code Caller.permissions()}). */
public final class StaffPermission {

	public static final String ONBOARDING_READ = "onboarding.read";
	public static final String ONBOARDING_REVIEW = "onboarding.review";
	public static final String ONBOARDING_HOLD = "onboarding.hold";
	public static final String ONBOARDING_APPROVE = "onboarding.approve";
	public static final String ONBOARDING_REJECT = "onboarding.reject";
	/** Take a claimed case back from its reviewer (e.g. the reviewer left). */
	public static final String ONBOARDING_REASSIGN = "onboarding.reassign";
	public static final String PII_REVEAL = "pii.reveal";
	public static final String AUDIT_READ = "audit.read";

	private StaffPermission() {
	}
}
