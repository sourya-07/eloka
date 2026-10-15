package in.eko.eloka.gateway.internal.handler.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.reference.Option;
import in.eko.eloka.reference.ReferenceData;
import org.springframework.stereotype.Component;

/** Dropdown lookups: 387 states, 519 shop types, 155 banks (with {@code dependent_params}). */
@Component
class LookupHandlers implements EpsHandler {

	private final ReferenceData reference;

	LookupHandlers(ReferenceData reference) {
		this.reference = reference;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.interaction(387), RouteKey.interaction(519), RouteKey.interaction(155));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.SESSION; // also needed during self-onboarding
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		List<Option> options = switch (call.request().interactionId().orElse("")) {
			case "387" -> reference.states();
			case "519" -> reference.shopTypes();
			default -> reference.banks();
		};
		return EpsEnvelope.list(options.stream().map(LookupHandlers::element).toList());
	}

	private static Map<String, Object> element(Option option) {
		Map<String, Object> element = new LinkedHashMap<>();
		element.put("value", option.value());
		element.put("label", option.label());
		if (!option.extra().isEmpty()) {
			element.put("dependent_params",
					option.extra().entrySet().stream().map(e -> Map.of("name", e.getKey(), "value", e.getValue())).toList());
		}
		return element;
	}
}
