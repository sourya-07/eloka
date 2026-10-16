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
	private final AuditLog audit;

	StaffService(StaffUserRepository staff, OtpService otps, SessionService sessions, GoogleIdentityVerifier google,
			StaffProperties properties, IdentityProperties identityProperties, BlindIndex index, AuditLog audit) {
		this.staff = staff;
		this.otps = otps;
		this.sessions = sessions;
		this.google = google;
		this.properties = properties;
		this.identityProperties = identityProperties;
		this.index = index;
		this.audit = audit;
	}

	@Transactional
	OtpDispatch sendOtp(String mobile, String clientIp) {
		requireOtpLogin();
		String normalized = Mobiles.normalize(mobile);
		// Same answer for unknown numbers: no list of staff mobiles can be probed.
		String code = staff.findByMobileHash(index.hash("staff_mobile", normalized)).isPresent()
				? otps.issueStaff(IdentityApi.PLATFORM_ORG_ID, normalized, clientIp)
				: null;
		return new OtpDispatch(normalized, identityProperties.otpLength(),
				identityProperties.exposeOtpInResponse() ? code : null);
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
		if (identity.isEmpty() || !identity.get().emailVerified() || identity.get().email() == null
				|| (domain != null && !domain.isBlank() && !domain.equalsIgnoreCase(identity.get().hostedDomain()))) {
			audit.record(new AuditEntry("STAFF_LOGIN_REFUSED", "ANONYMOUS", null, IdentityApi.PLATFORM_ORG_ID, "STAFF",
					null, Map.of("method", "GOOGLE")));
			return new StaffLoginResult.NotAllowed();
		}
		return staff.findByEmail(identity.get().email().toLowerCase(Locale.ROOT))
			.filter(StaffUser::isActive)
			.<StaffLoginResult>map(user -> open(user, "GOOGLE"))
			.orElseGet(StaffLoginResult.NotAllowed::new);
	}

	/** Creates or updates a staff account (bootstrap command and local seed). */
	@Transactional
	StaffView upsert(String email, String displayName, String mobile, Set<StaffRole> roles, boolean active) {
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		if (!normalizedEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw ValidationException.field("email", "Enter a valid email address");
		}
		if (roles.isEmpty()) {
			throw ValidationException.field("roles", "Give the staff member at least one role");
		}
		StaffUser user = staff.findByEmail(normalizedEmail)
			.orElseGet(() -> staff.save(new StaffUser(normalizedEmail, displayName)));
		user.update(displayName, mobile == null ? null : index.hash("staff_mobile", Mobiles.normalize(mobile)), roles,
				active);
		audit.record(new AuditEntry("STAFF_ACCOUNT_SAVED", "SYSTEM", null, IdentityApi.PLATFORM_ORG_ID, "STAFF",
				user.getId().toString(), Map.of("roles", roles.toString(), "active", Boolean.toString(active))));
		return user.view();
	}

	@Transactional(readOnly = true)
	Optional<StaffView> find(UUID staffId) {
		return staff.findById(staffId).map(StaffUser::view);
	}

	/** Permissions of an active staff member, read on every request so revoked roles apply at once. */
	@Transactional(readOnly = true)
	Optional<Set<String>> activePermissions(UUID staffId) {
		return staff.findById(staffId).filter(StaffUser::isActive).map(StaffUser::permissions);
	}

	private StaffLoginResult open(StaffUser user, String method) {
		// The session's encrypted "mobile" slot holds the staff id (emails can exceed the column)
		var tokens = sessions.open(SessionKind.STAFF, IdentityApi.PLATFORM_ORG_ID, user.getId(), user.getId().toString());
		audit.record(new AuditEntry("STAFF_LOGIN_SUCCEEDED", "STAFF", user.getId().toString(), IdentityApi.PLATFORM_ORG_ID,
				"STAFF", user.getId().toString(), Map.of("method", method)));
		return new StaffLoginResult.Success(tokens, user.view());
	}

	private void requireOtpLogin() {
		if (!properties.otpLoginEnabled()) {
			throw new NotFoundException("NOT_FOUND", "Not found");
		}
	}

	@Override
	public List<String> productionViolations() {
		return properties.otpLoginEnabled() ? List.of("eloka.staff.otp-login-enabled=true lets staff log in without Google")
				: List.of();
	}
}
