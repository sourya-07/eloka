package in.eko.eloka.identity.internal;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.IdentityProperties;
import in.eko.eloka.identity.OtpDispatch;
import in.eko.eloka.identity.StaffLoginResult;
import in.eko.eloka.identity.StaffProperties;
import in.eko.eloka.identity.StaffRole;
import in.eko.eloka.identity.StaffView;
import in.eko.eloka.integration.GoogleIdentityVerifier;
import in.eko.eloka.shared.config.SafetyCheck;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Masking;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.SessionKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff accounts and sign-in: Google in every environment, OTP to a registered mobile only when
 * {@code eloka.staff.otp-login-enabled} (local/test; production refuses to start with it on).
 */
@Service
class StaffService implements SafetyCheck {

	private final StaffUserRepository staff;
	private final OtpService otps;
	private final SessionService sessions;
	private final GoogleIdentityVerifier google;
	private final StaffProperties properties;
	private final IdentityProperties identityProperties;
	private final BlindIndex index;
	private final FieldCipher cipher;
	private final AuditLog audit;

	StaffService(StaffUserRepository staff, OtpService otps, SessionService sessions, GoogleIdentityVerifier google,
			StaffProperties properties, IdentityProperties identityProperties, BlindIndex index, FieldCipher cipher,
			AuditLog audit) {
		this.staff = staff;
		this.otps = otps;
		this.sessions = sessions;
		this.google = google;
		this.properties = properties;
		this.identityProperties = identityProperties;
		this.index = index;
		this.cipher = cipher;
		this.audit = audit;
	}

	@Transactional
	OtpDispatch sendOtp(String mobile, String clientIp) {
		requireOtpLogin();
		String normalized = Mobiles.normalize(mobile);
		// Unknown numbers get a silent challenge: the same rate limit and the same verify answers,
		// so no list of staff mobiles can be probed.
		boolean known = staff.findByMobileHash(index.hash("staff_mobile", normalized)).isPresent();
		String code = otps.issueStaff(IdentityApi.PLATFORM_ORG_ID, normalized, clientIp, known);
		return new OtpDispatch(normalized, identityProperties.otpLength(),
				identityProperties.exposeOtpInResponse() && known ? code : null);
	}

	@Transactional(noRollbackFor = RuntimeException.class)
	StaffLoginResult verifyOtp(String mobile, String code) {
		requireOtpLogin();
		String normalized = Mobiles.normalize(mobile);
		OtpService.Check check = otps.checkStaff(IdentityApi.PLATFORM_ORG_ID, normalized, code);
		if (check instanceof OtpService.Check.NoLiveOtp) {
			return new StaffLoginResult.Expired();
		}
		if (check instanceof OtpService.Check.Wrong wrong) {
			return new StaffLoginResult.WrongCode(wrong.attemptsLeft());
		}
		return staff.findByMobileHash(index.hash("staff_mobile", normalized))
			.filter(StaffUser::isActive)
			.<StaffLoginResult>map(user -> open(user, "OTP"))
			.orElseGet(StaffLoginResult.NotAllowed::new);
	}

	@Transactional
	StaffLoginResult googleLogin(String idToken) {
		Optional<GoogleIdentityVerifier.GoogleIdentity> identity = google.verify(idToken);
		String domain = properties.googleDomain();
		// No domain configured = nobody: a personal Google account must never pass as staff
		if (identity.isEmpty() || !identity.get().emailVerified() || identity.get().email() == null || domain == null
				|| domain.isBlank() || !domain.equalsIgnoreCase(identity.get().hostedDomain())) {
			return refused(null, "TOKEN_OR_DOMAIN");
		}
		Optional<StaffUser> user = staff.findByEmailHash(emailHash(identity.get().email().toLowerCase(Locale.ROOT)));
		if (user.isEmpty()) {
			return refused(null, "UNKNOWN_ACCOUNT");
		}
		if (!user.get().isActive()) {
			return refused(user.get().getId().toString(), "ACCOUNT_DISABLED");
		}
		return open(user.get(), "GOOGLE");
	}

	/** Creates or updates a staff account (bootstrap command and local seed). */
	@Transactional
	StaffView upsert(String email, String displayName, String mobile, Set<StaffRole> roles, boolean active) {
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		if (!normalizedEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw ValidationException.field("email", "Enter a valid email address");
		}
		Optional<StaffUser> existing = staff.findByEmailHash(emailHash(normalizedEmail));
		if (existing.isEmpty() && roles.isEmpty()) {
			throw ValidationException.field("roles", "Give the staff member at least one role");
		}
		String masked = Masking.email(normalizedEmail);
		StaffUser user = existing.orElseGet(() -> staff.save(new StaffUser(cipher.encrypt(normalizedEmail),
				emailHash(normalizedEmail), masked, displayName == null || displayName.isBlank() ? masked : displayName)));
		user.update(displayName, mobile == null ? null : index.hash("staff_mobile", Mobiles.normalize(mobile)), roles,
				active);
		audit.record(new AuditEntry("STAFF_ACCOUNT_SAVED", "SYSTEM", null, IdentityApi.PLATFORM_ORG_ID, "STAFF",
				user.getId().toString(), Map.of("roles", user.view().roles().toString(), "active", Boolean.toString(active))));
		return user.view();
	}

	@Transactional(readOnly = true)
	Optional<StaffView> find(UUID staffId) {
		return staff.findById(staffId).map(StaffUser::view);
	}

	private StaffLoginResult open(StaffUser user, String method) {
		// The session's encrypted "mobile" slot holds the staff id (emails can exceed the column)
		var tokens = sessions.open(SessionKind.STAFF, IdentityApi.PLATFORM_ORG_ID, user.getId(), user.getId().toString());
		audit.record(new AuditEntry("STAFF_LOGIN_SUCCEEDED", "STAFF", user.getId().toString(), IdentityApi.PLATFORM_ORG_ID,
				"STAFF", user.getId().toString(), Map.of("method", method)));
		return new StaffLoginResult.Success(tokens, user.view());
	}

	private byte[] emailHash(String normalizedEmail) {
		return index.hash("staff_email", normalizedEmail);
	}

	/** Every refused Google sign-in is audited (a disabled account trying again shows up). Never the email. */
	private StaffLoginResult refused(String staffId, String reason) {
		audit.record(new AuditEntry("STAFF_LOGIN_REFUSED", "ANONYMOUS", null, IdentityApi.PLATFORM_ORG_ID, "STAFF",
				staffId, Map.of("method", "GOOGLE", "reason", reason)));
		return new StaffLoginResult.NotAllowed();
	}

	private void requireOtpLogin() {
		if (!properties.otpLoginEnabled()) {
			throw new NotFoundException("NOT_FOUND", "Not found");
		}
	}

	@Override
	public List<String> productionViolations() {
		List<String> violations = new java.util.ArrayList<>();
		if (properties.otpLoginEnabled()) {
			violations.add("eloka.staff.otp-login-enabled=true lets staff log in without Google");
		}
		if (properties.googleClientId() != null && !properties.googleClientId().isBlank()
				&& (properties.googleDomain() == null || properties.googleDomain().isBlank())) {
			violations.add("eloka.staff.google-domain is empty: staff Google sign-in would refuse everyone");
		}
		return violations;
	}
}
