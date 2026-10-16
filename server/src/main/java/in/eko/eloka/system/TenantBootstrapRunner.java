package in.eko.eloka.system;

import in.eko.eloka.shared.error.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Profile {@code bootstrap}: creates the first tenant from {@code ELOKA_BOOTSTRAP_*} and exits
 * (0 = created or already there, 1 = bad input). See docs/deploy/RUNBOOK.md.
 */
@Component
@Profile("bootstrap")
class TenantBootstrapRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TenantBootstrapRunner.class);

	private final TenantBootstrap bootstrap;
	private final BootstrapProperties properties;
	private final ConfigurableApplicationContext context;

	TenantBootstrapRunner(TenantBootstrap bootstrap, BootstrapProperties properties, ConfigurableApplicationContext context) {
		this.bootstrap = bootstrap;
		this.properties = properties;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		int code = 0;
		try {
			TenantBootstrap.Outcome outcome = bootstrap.run(properties);
			log.info(outcome.created() ? "Tenant created: org {} at https://{} (Local Admin can log in with OTP)"
					: "Tenant already exists: org {} at https://{}; nothing changed", outcome.orgId(), outcome.hostname());
		}
		catch (DomainException ex) {
			log.error("Bootstrap refused: {}", ex.getMessage());
			code = 1;
		}
		int exitCode = code;
		System.exit(SpringApplication.exit(context, () -> exitCode));
	}
}
