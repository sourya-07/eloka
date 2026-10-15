package in.eko.eloka.tenant.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import in.eko.eloka.shared.config.SafetyCheck;
import in.eko.eloka.tenant.OrgTokens;
import in.eko.eloka.tenant.TenantProperties;
import org.springframework.stereotype.Component;

/** {@code <orgId>.<expiryEpochSeconds>.<base64url HMAC-SHA256>}. */
@Component
class HmacOrgTokens implements OrgTokens, SafetyCheck {

	static final String DEVELOPMENT_KEY = "ZWxva2EtbG9jYWwtb3JnLXRva2VuLWtleS0zMmJ5dGU=";
	static final String DEVELOPMENT_META_SECRET = "eloka-local-org-meta-secret";

	private final TenantProperties properties;
	private final Clock clock;
	private final SecretKeySpec key;

	HmacOrgTokens(TenantProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		byte[] raw = Base64.getDecoder().decode(properties.orgTokenKey());
		if (raw.length != 32) {
			throw new IllegalStateException("eloka.tenant.org-token-key must be 32 bytes (base64)");
		}
		this.key = new SecretKeySpec(raw, "HmacSHA256");
	}

	@Override
	public String issue(long orgId) {
		long expiry = clock.instant().plus(properties.orgTokenTtl()).getEpochSecond();
		String payload = orgId + "." + expiry;
		return payload + "." + sign(payload);
	}

	@Override
	public Optional<Long> verify(String token) {
		if (token == null) {
			return Optional.empty();
		}
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			return Optional.empty();
		}
		String payload = parts[0] + "." + parts[1];
		byte[] expected = sign(payload).getBytes(StandardCharsets.US_ASCII);
		if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.US_ASCII))) {
			return Optional.empty();
		}
		try {
			long expiry = Long.parseLong(parts[1]);
			if (clock.instant().getEpochSecond() > expiry) {
				return Optional.empty();
			}
			return Optional.of(Long.parseLong(parts[0]));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

	@Override
	public List<String> productionViolations() {
		List<String> problems = new ArrayList<>();
		if (DEVELOPMENT_KEY.equals(properties.orgTokenKey())) {
			problems.add("eloka.tenant.org-token-key is the published development key");
		}
		if (DEVELOPMENT_META_SECRET.equals(properties.orgMetaSecret())) {
			problems.add("eloka.tenant.org-meta-secret is the published development secret");
		}
		return problems;
	}

	private String sign(String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
