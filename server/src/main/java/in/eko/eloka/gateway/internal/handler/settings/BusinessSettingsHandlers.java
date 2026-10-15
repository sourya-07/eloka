package in.eko.eloka.gateway.internal.handler.settings;

import java.util.List;
import java.util.Map;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsParams;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.UserTypes;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.tenant.OrgSettings;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Business Settings tiles. Values are stored as org settings; the client needs them as strings "0"/"1". */
final class BusinessSettingsHandlers {

	static final String ACCOUNT_VERIFICATION = "account_verification_optional"; // "0" mandatory, "1" optional
	static final String REFUND_METHOD = "refund_with_agent_pin";              // "0" customer OTP, "1" agent PIN
	static final String CDM_CHARGES = "cdm_charges_from_agents";             // "1" agents pay, "0" admin pays
	static final String COMMISSION_FREQUENCY = "commission_frequency";

	private BusinessSettingsHandlers() {
	}

	/**
	 * 737, one interaction for three screens: {@code operation} 0 = read (with
	 * {@code operation_type 3}: refund method, else account verification), 2 = save account
	 * verification, 3 = save refund method, 4 = save cash-deposit charges, 5 = read cash-deposit charges.
	 */
	@Component
	static class Toggles implements EpsHandler {

		private final OrgSettings settings;

		Toggles(OrgSettings settings) {
			this.settings = settings;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(737));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.ORG_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			EpsParams p = call.params();
			int operation = p.integer("operation").orElseThrow(() -> ValidationException.field("operation", "operation is required"));
			return switch (operation) {
				case 0 -> {
					String key = "3".equals(p.string("operation_type").orElse("")) ? REFUND_METHOD : ACCOUNT_VERIFICATION;
					yield value("otp_verification_token", read(caller, key, "0"), "Success");
				}
				case 2 -> save(caller, ACCOUNT_VERIFICATION, flag(p, "otp_verification_token"), "otp_verification_token",
						"Account verification setting updated");
				case 3 -> save(caller, REFUND_METHOD, flag(p, "otp_verification_token"), "otp_verification_token",
						"Refund method updated");
				case 4 -> save(caller, CDM_CHARGES, flag(p, "cdm_charges"), "cdm_charges", "Cash deposit setting updated");
				case 5 -> value("cdm_charges", read(caller, CDM_CHARGES, "1"), "Success");
				default -> throw ValidationException.field("operation", "Unknown operation");
			};
		}

		private String read(Caller caller, String key, String fallback) {
			return settings.get(caller.orgId(), key).map(v -> v.replace("\"", "")).orElse(fallback);
		}

		private EpsResponse save(Caller caller, String key, String value, String field, String message) {
			settings.put(caller.orgId(), key, "\"" + value + "\"", caller);
			return value(field, value, message);
		}

		private static EpsResponse value(String field, String value, String message) {
			return EpsEnvelope.ok().message(message).data(Map.of(field, value)).build();
		}

		private static String flag(EpsParams p, String field) {
			String value = p.requireString(field);
			if (!"0".equals(value) && !"1".equals(value)) {
				throw ValidationException.field(field, "Choose one of the options");
			}
			return value;
		}
	}

	/**
	 * 726 commission frequency: {@code source_flag} "2" daily / "1" monthly, for the org ("Self")
	 * or for chosen distributors ({@code CspList}).
	 */
	@Component
	static class CommissionFrequency implements EpsHandler {

		private final OrgSettings settings;
		private final NetworkQueries network;
		private final JsonMapper json;

		CommissionFrequency(OrgSettings settings, NetworkQueries network, JsonMapper json) {
			this.settings = settings;
			this.network = network;
			this.json = json;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(726));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.ORG_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			EpsParams p = call.params();
			if (p.string("service_code").isPresent()) {
				throw ValidationException.field("service_code", "Please use Pricing & Commissions to set distributor commission.");
			}
			String frequency = p.requireString("source_flag");
			if (!"1".equals(frequency) && !"2".equals(frequency)) {
				throw ValidationException.field("source_flag", "Choose daily or monthly");
			}
			ObjectNode stored = settings.get(caller.orgId(), COMMISSION_FREQUENCY)
				.map(v -> (ObjectNode) json.readTree(v))
				.orElseGet(() -> json.createObjectNode().put("default", "1"));
			ObjectNode perDistributor = stored.has("distributors") ? (ObjectNode) stored.get("distributors") : stored.putObject("distributors");
			boolean forDistributors = "2".equals(p.string("communication").orElse("")) && p.string("CspList").isPresent();
			if (forDistributors) {
				for (String code : p.requireString("CspList").split(",")) {
					var user = network.findInScope(caller, code.trim());
					if (user.userType() != UserTypes.DISTRIBUTOR && user.userType() != UserTypes.SUPER_DISTRIBUTOR) {
						throw ValidationException.field("CspList", "Choose distributors only");
					}
					perDistributor.put(user.userCode(), frequency);
				}
			}
			else {
				stored.put("default", frequency);
			}
			settings.put(caller.orgId(), COMMISSION_FREQUENCY, json.writeValueAsString(stored), caller);
			return EpsEnvelope.ok().message("Commission frequency updated").build();
		}
	}
}
