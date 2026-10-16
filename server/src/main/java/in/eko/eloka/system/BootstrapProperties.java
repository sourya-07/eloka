package in.eko.eloka.system;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First tenant for a fresh installation ({@code eloka.bootstrap}, set as {@code ELOKA_BOOTSTRAP_*}
 * env vars). Used only by the {@code bootstrap} profile.
 *
 * @param subdomain label of the tenant site, e.g. {@code shlok} → {@code shlok.<root domain>}
 * @param orgName legal name of the business
 * @param appName name shown in the portal (defaults to the org name)
 * @param adminMobile 10-digit mobile of the Local Admin (logs in with OTP)
 * @param adminName Local Admin's name
 * @param adminEmail optional e-mail
 * @param extraHostnames optional custom domains (their DNS must point at the edge)
 * @param supportEmail optional support e-mail shown to agents
 * @param supportPhone optional support phone shown to agents
 */
@ConfigurationProperties("eloka.bootstrap")
record BootstrapProperties(String subdomain, String orgName, String appName, String adminMobile, String adminName,
		String adminEmail, List<String> extraHostnames, String supportEmail, String supportPhone) {

	BootstrapProperties {
		extraHostnames = extraHostnames == null ? List.of() : List.copyOf(extraHostnames);
	}
}
