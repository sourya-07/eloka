package in.eko.eloka.shared.crypto;

import java.util.ArrayList;
import java.util.List;
import in.eko.eloka.shared.config.SafetyCheck;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the crypto helpers and refuses the published development keys in production. */
@Configuration
public class CryptoConfig implements SafetyCheck {

	/** The keys committed in application-local.yaml. Never valid outside a laptop. */
	static final List<String> DEVELOPMENT_KEYS = List.of(
			"ZWxva2EtbG9jYWwtZmllbGQta2V5LTMyLWJ5dGVzISE=",
			"ZWxva2EtbG9jYWwtaW5kZXgta2V5LTMyLWJ5dGVzISE=");

	private final CryptoProperties properties;

	public CryptoConfig(CryptoProperties properties) {
		this.properties = properties;
	}

	@Bean
	FieldCipher fieldCipher() {
		return new FieldCipher(properties.fieldKey());
	}

	@Bean
	BlindIndex blindIndex() {
		return new BlindIndex(properties.indexKey());
	}

	@Override
	public List<String> productionViolations() {
		List<String> problems = new ArrayList<>();
		if (DEVELOPMENT_KEYS.contains(properties.fieldKey())) {
			problems.add("eloka.crypto.field-key is the published development key");
		}
		if (DEVELOPMENT_KEYS.contains(properties.indexKey())) {
			problems.add("eloka.crypto.index-key is the published development key");
		}
		return problems;
	}
}
