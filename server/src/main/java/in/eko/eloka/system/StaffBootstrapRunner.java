package in.eko.eloka.system;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.StaffRole;
import in.eko.eloka.shared.error.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Profile {@code staff-bootstrap}: creates or updates one Eko staff account from
 * {@code ELOKA_STAFF_EMAIL}, {@code ELOKA_STAFF_NAME}, {@code ELOKA_STAFF_ROLES} (comma-separated:
 * REVIEWER, APPROVER, AUDITOR, ADMIN) and {@code ELOKA_STAFF_ACTIVE} (default true), then exits.
 * Staff then sign in with Google using that email. See docs/deploy/RUNBOOK.md.
 */
@Component
@Profile("staff-bootstrap")
class StaffBootstrapRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StaffBootstrapRunner.class);

	private final IdentityApi identity;
	private final ConfigurableApplicationContext context;
	private final String email;
	private final String name;
	private final String roles;
	private final boolean active;

	StaffBootstrapRunner(IdentityApi identity, ConfigurableApplicationContext context,
			@Value("${ELOKA_STAFF_EMAIL:}") String email, @Value("${ELOKA_STAFF_NAME:}") String name,
			@Value("${ELOKA_STAFF_ROLES:}") String roles, @Value("${ELOKA_STAFF_ACTIVE:true}") boolean active) {
		this.identity = identity;
		this.context = context;
		this.email = email;
		this.name = name;
		this.roles = roles;
		this.active = active;
	}

	@Override
	public void run(ApplicationArguments args) {
		int code = 0;
		try {
			Set<StaffRole> parsed = EnumSet.noneOf(StaffRole.class);
			Arrays.stream(roles.split(",")).map(String::trim).filter(r -> !r.isEmpty())
				.forEach(r -> parsed.add(StaffRole.valueOf(r.toUpperCase())));
			var staff = identity.saveStaff(email, name.isBlank() ? email : name, null, parsed, active);
			log.info("Staff account saved: {} roles={} active={}", staff.id(), staff.roles(), staff.active());
		}
		catch (DomainException | IllegalArgumentException ex) {
			log.error("Staff bootstrap refused: {}", ex.getMessage());
			code = 1;
		}
		int exitCode = code;
		System.exit(SpringApplication.exit(context, () -> exitCode));
	}
}
