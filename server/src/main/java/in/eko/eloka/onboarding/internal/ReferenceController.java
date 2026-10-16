package in.eko.eloka.onboarding.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.reference.Option;
import in.eko.eloka.reference.ReferenceData;
import in.eko.eloka.whitelabel.SubdomainAvailability;
import in.eko.eloka.whitelabel.WhiteLabelApi;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reference lists (A19, A20) and sub-domain availability (A26) for applicants. */
@RestController
@RequestMapping("/v1/onboarding")
class ReferenceController {

	private final ReferenceData reference;
	private final WhiteLabelApi whiteLabel;

	ReferenceController(ReferenceData reference, WhiteLabelApi whiteLabel) {
		this.reference = reference;
		this.whiteLabel = whiteLabel;
	}

	/** A19: label/value plus {@code bank_id}, the shape AddBankAccountStep already reads. */
	@GetMapping("/reference/banks")
	ResponseEntity<Map<String, Object>> banks() {
		return cached(reference.banks());
	}

	/** A20 */
	@GetMapping("/reference/states")
	ResponseEntity<Map<String, Object>> states() {
		return cached(reference.states());
	}

	/** A26 */
	@GetMapping("/subdomains/availability")
	SubdomainAvailability availability(@RequestParam(name = "label", required = false) String label) {
		WebSupport.applicant();
		return whiteLabel.availability(label);
	}

	private static ResponseEntity<Map<String, Object>> cached(List<Option> options) {
		WebSupport.applicant();
		List<Map<String, Object>> items = options.stream().map(option -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("label", option.label());
			item.put("value", option.value());
			item.putAll(option.extra());
			return item;
		}).toList();
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(24)).cachePrivate())
			.body(Map.of("items", items));
	}
}
