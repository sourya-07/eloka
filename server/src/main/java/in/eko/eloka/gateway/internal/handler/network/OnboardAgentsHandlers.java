package in.eko.eloka.gateway.internal.handler.network;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.envelope.UploadedFile;
import in.eko.eloka.network.AgentOnboarding;
import in.eko.eloka.network.AgentSignup;
import in.eko.eloka.network.OnboardResult;
import in.eko.eloka.network.UserTypes;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.wallet.LedgerPosting;
import in.eko.eloka.wallet.WalletApi;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** "Onboard Agents": by form (one or a few, optionally demo accounts) and by spreadsheet. */
final class OnboardAgentsHandlers {

	static final String V1 = "/ekoicici/v1";

	private OnboardAgentsHandlers() {
	}

	/** EPS applicant_type → user type ("0" retailer, "2" distributor). */
	static int userTypeOf(String applicantType) {
		return switch (applicantType == null ? "" : applicantType.trim()) {
			case "0" -> UserTypes.MERCHANT;
			case "2" -> UserTypes.DISTRIBUTOR;
			default -> throw ValidationException.field("applicant_type", "Choose retailer or distributor");
		};
	}

	/** {@code {message, data:{csp_list, processed_records, failed_count}}}, always status 0. */
	static EpsResponse summary(List<OnboardResult> results) {
		List<Map<String, Object>> rows = new ArrayList<>();
		long ok = 0;
		for (OnboardResult r : results) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("name", r.name());
			row.put("mobile", r.mobile());
			row.put("status", r.succeeded() ? "Success" : "Failed");
			row.put("reason", r.succeeded() ? "" : r.reason());
			if (r.succeeded()) {
				row.put("user_code", r.user().userCode());
				ok++;
			}
			rows.add(row);
		}
		long failed = results.size() - ok;
		String message = ok == results.size() ? "All agents onboarded"
				: ok == 0 ? "No agents onboarded" : ok + " of " + results.size() + " agents onboarded";
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("csp_list", rows);
		data.put("processed_records", ok);
		data.put("failed_count", failed);
		return EpsEnvelope.ok().message(message).data(data).build();
	}

	/** dojson tf POST /network/agent/multiple_onboarding: {applicant_type, CspList:[...]}. */
	@Component
	static class ByForm implements EpsHandler {

		private final AgentOnboarding onboarding;
		private final WalletApi wallets;

		ByForm(AgentOnboarding onboarding, WalletApi wallets) {
			this.onboarding = onboarding;
			this.wallets = wallets;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V1, HttpMethod.POST, "/network/agent/multiple_onboarding"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			int userType = userTypeOf(call.params().string("applicant_type").orElse(""));
			JsonNode list = call.params().node("CspList")
				.orElseThrow(() -> ValidationException.field("CspList", "Add at least one agent"));
			List<AgentSignup> agents = new ArrayList<>();
			List<BigDecimal> demoCredits = new ArrayList<>();
			for (JsonNode row : list) {
				boolean demo = row.path("is_demo_account").asBoolean(false);
				String validity = row.path("demo_account_validity").asString("");
				agents.add(new AgentSignup(row.path("agent_name").asString(""), row.path("agent_mobile").asString(""),
						row.path("dist_mobile").asString(null), demo, validity.isBlank() ? null : Integer.valueOf(validity)));
				String credit = row.path("demo_credit_limit").asString("");
				demoCredits.add(demo && !credit.isBlank() ? new BigDecimal(credit) : BigDecimal.ZERO);
			}
			List<OnboardResult> results = onboarding.onboard(caller, userType, agents);
			for (int i = 0; i < results.size(); i++) {
				OnboardResult result = results.get(i);
				if (result.succeeded() && demoCredits.get(i).signum() > 0) {
					wallets.post(new LedgerPosting(caller.orgId(), result.user().id(), true, demoCredits.get(i), "DEMO-CREDIT",
							"Demo credit"));
				}
			}
			return summary(results);
		}
	}

	/** customupload tf POST /network/agent/bulk_onboarding: multipart {formdata, file_name}. */
	@Component
	static class ByFile implements EpsHandler {

		private final AgentOnboarding onboarding;

		ByFile(AgentOnboarding onboarding) {
			this.onboarding = onboarding;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V1, HttpMethod.POST, "/network/agent/bulk_onboarding"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			int userType = userTypeOf(call.params().string("applicant_type").orElse(""));
			UploadedFile file = call.files().stream().findFirst()
				.orElseThrow(() -> ValidationException.field("file_name", "Choose a file to upload"));
			return summary(onboarding.onboard(call.caller(), userType, AgentSheetReader.read(file)));
		}
	}
}
