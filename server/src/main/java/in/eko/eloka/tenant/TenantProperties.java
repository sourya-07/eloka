package in.eko.eloka.tenant;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param rootDomain platform root, e.g. {@code eloka.in}; tenants live at {@code <label>.<root>}
 * @param orgMetaSecret shared secret the client's server sends with the org lookup (677)
 * @param orgTokenKey base64 32-byte HMAC key for {@link OrgTokens}
 * @param orgTokenTtl how long an org token is valid (longer than the client's org cache)
 */
@ConfigurationProperties("eloka.tenant")
public record TenantProperties(String rootDomain, String orgMetaSecret, String orgTokenKey, Duration orgTokenTtl) {

	public TenantProperties {
		orgTokenTtl = orgTokenTtl == null ? Duration.ofDays(30) : orgTokenTtl;
	}
}
