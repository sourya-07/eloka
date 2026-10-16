package in.eko.eloka.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import in.eko.eloka.TestcontainersConfiguration;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.tenant.OrgApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TenantBootstrapIntegrationTest {

	@Autowired TenantBootstrap bootstrap;
	@Autowired OrgApi orgs;
	@Autowired UserDirectory users;

	private static BootstrapProperties request(String subdomain, String mobile) {
		return new BootstrapProperties(subdomain, "Shlok Enterprise", null, mobile, "Shlok Admin", null,
				List.of("Portal.Shlok.example"), "help@shlok.example", null);
	}

	@Test
	void createsTheOrgItsDomainsAndTheLocalAdminOnce() {
		TenantBootstrap.Outcome first = bootstrap.run(request("shlok", "9877700001"));
		assertThat(first.created()).isTrue();
		assertThat(orgs.findByHostname(first.hostname())).isPresent();
		assertThat(orgs.findByHostname("portal.shlok.example")).isPresent();
		var admin = users.findByMobile(first.orgId(), "9877700001").orElseThrow();
		assertThat(admin.role()).isEqualTo(in.eko.eloka.shared.security.Role.ORG_ADMIN);

		TenantBootstrap.Outcome again = bootstrap.run(request("shlok", "9877700002"));
		assertThat(again.created()).isFalse();
		assertThat(again.orgId()).isEqualTo(first.orgId());
		assertThat(users.findByMobile(first.orgId(), "9877700002")).isEmpty();
	}

	@Test
	void refusesABadSubdomainOrMissingAdmin() {
		assertThatThrownBy(() -> bootstrap.run(request("Bad_Label", "9877700003"))).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> bootstrap.run(request("nomobile", null))).isInstanceOf(ValidationException.class)
			.hasMessageContaining("ELOKA_BOOTSTRAP_ADMIN_MOBILE");
	}
}
