package in.eko.eloka.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.TestcontainersConfiguration;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.tenant.NewOrg;
import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.tenant.OrgStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P5: self-onboarding end to end, distributor plan, assisted onboarding and the admin funnel. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentOnboardingIntegrationTest {

	/** A 1x1 PNG, so uploads pass the content check. */
	private static final byte[] PNG = Base64.getDecoder()
		.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

	private static final String ADMIN = "9855500003";
	private static final String DIST = "9855500002";

	@Autowired MockMvcTester mvc;
	@Autowired JsonMapper json;
	@Autowired OrgApi orgs;
	@Autowired UserCommands users;

	private long orgId;
	private String orgToken;

	@BeforeAll
	void seed() throws Exception {
		orgId = orgs.createOrg(new NewOrg("Kyc Org", "Kyc", null, OrgStatus.ACTIVE, List.of("kyc.eloka.test"), Map.of())).id();
		users.createUser(new NewUser(orgId, ADMIN, "Admin", null, 24, true, UserStatus.ACTIVE, null, null, false,
				ShopView.empty(), null));
		var dist = users.createUser(new NewUser(orgId, DIST, "Dist", null, 1, false, UserStatus.ACTIVE, null, null, false,
				ShopView.empty(), null));
		// The distributor itself would be onboarding; finish it so it behaves like an established distributor.
		completeAll(dist.id());
		orgToken = post("/", Map.of("interaction_type_id", "677", "sub_domain", "kyc", "secret", "test-org-meta-secret"), null)
			.get("org_token").asString();
	}

	@Autowired in.eko.eloka.agentonboarding.AgentOnboardingApi onboardingApi;

	private void completeAll(java.util.UUID userId) {
		onboardingApi.completeSteps(orgId, userId, java.util.EnumSet.allOf(in.eko.eloka.agentonboarding.KycStep.class));
	}

	@Test
	void retailerSelfOnboardsFromNewNumberToHome() throws Exception {
		String mobile = "9855500101";
		String token = login(mobile);
		assertThat(post("/", Map.of("interaction_type_id", "151", "mobile", mobile), token).get("response_type_id").asInt())
			.isEqualTo(370);

		assertThat(step(token, Map.of("interaction_type_id", 521, "mobile", mobile, "applicant_type", "0")).asInt()).isEqualTo(1566);
		JsonNode detail = post("/", Map.of("interaction_type_id", "151", "mobile", mobile), token).at("/data/user_detail");
		assertThat(detail.get("onboarding").asInt()).isEqualTo(1);
		assertThat(detail.get("onboarding_steps")).hasSize(7);
		assertThat(detail.get("role_list").asString()).startsWith("12400");

		assertThat(upload(token, mobile, 1, Map.of(), "file1", "file2").asInt()).isEqualTo(1569);
		assertThat(upload(token, mobile, 2, Map.of("doc_id", "ABCDE1234F"), "file1").asInt()).isEqualTo(1569);
		assertThat(step(token, Map.of("interaction_type_id", 298, "mobile", mobile, "latlong", "12.97,77.59", "shop_type", "1"))
			.asInt()).isEqualTo(1076);
		assertThat(upload(token, mobile, 3, Map.of(), "file1").asInt()).isEqualTo(1569);
		Map<String, Object> business = new LinkedHashMap<>(Map.of("interaction_type_id", 522, "mobile", mobile, "name",
				"Asha Stores", "authorized_signatory_name", "Asha Rao", "current_address_line1", "1 MG Road",
				"current_address_state", "Karnataka", "current_address_district", "Bengaluru", "current_address_pincode",
				"560001", "company_type", "1"));
		assertThat(step(token, business).asInt()).isEqualTo(1567);
		assertThat(step(token, Map.of("interaction_type_id", 454, "mobile", mobile, "bank_code", "SBIN", "account",
				"123456789012", "ifsc", "SBIN0001234")).asInt()).isEqualTo(1445);
		assertThat(upload(token, mobile, 7, Map.of(), "file1").asInt()).isEqualTo(1569);

		assertThat(step(token, Map.of("interaction_type_id", 170, "mobile", mobile)).asInt()).isEqualTo(1646);
		JsonNode key = post("/transactions/do", Map.of("interaction_type_id", 10005), token).get("data");
		String encoded = encode("4826", key.get("pintwin_key").asString()) + "|" + key.get("key_id").asLong();
		assertThat(step(token, Map.of("interaction_type_id", 5, "mobile", mobile, "first_okekey", encoded, "second_okekey",
				encoded, "is_pintwin_user", 0)).asInt()).isEqualTo(9);

		JsonNode esign = post("/", Map.of("interaction_type_id", 287, "mobile", mobile, "agreement_id", 5), token);
		assertThat(esign.get("response_type_id").asInt()).isEqualTo(1069);
		assertThat(step(token, Map.of("interaction_type_id", 293, "mobile", mobile, "document_id",
				esign.at("/data/document_id").asString(), "esign_completed", true)).asInt()).isEqualTo(1615);

		JsonNode done = post("/", Map.of("interaction_type_id", "151", "mobile", mobile), token).at("/data/user_detail");
		assertThat(done.get("onboarding").asInt()).isZero();
		assertThat(done.get("name").asString()).isEqualTo("Asha Rao");
	}

	@Test
	void duplicatePanAndWeakPinAreRejected() throws Exception {
		String first = "9855500111";
		String second = "9855500112";
		for (String m : List.of(first, second)) {
			step(login(m), Map.of("interaction_type_id", 521, "mobile", m, "applicant_type", "0"));
		}
		String t1 = login(first);
		upload(t1, first, 2, Map.of("doc_id", "PQRST6789K"), "file1");
		JsonNode dup = uploadRaw(login(second), second, 2, Map.of("doc_id", "PQRST6789K"), "file1");
		assertThat(dup.get("error_code").asString()).isEqualTo("PAN_ALREADY_REGISTERED");

		JsonNode key = post("/transactions/do", Map.of("interaction_type_id", 10005), t1).get("data");
		String weak = encode("1234", key.get("pintwin_key").asString()) + "|" + key.get("key_id").asLong();
		JsonNode res = post("/", Map.of("interaction_type_id", 5, "mobile", first, "first_okekey", weak, "second_okekey", weak), t1);
		assertThat(res.get("status").asInt()).isEqualTo(1);
		assertThat(res.get("message").asString()).contains("harder to guess");
	}

	@Test
	void distributorPlanUsesDigiLocker() throws Exception {
		String mobile = "9855500121";
		String token = login(mobile);
		step(token, Map.of("interaction_type_id", 521, "mobile", mobile, "applicant_type", "2"));
		JsonNode detail = post("/", Map.of("interaction_type_id", "151", "mobile", mobile), token).at("/data/user_detail");
		assertThat(detail.get("role_list").asString()).startsWith("24000");

		JsonNode link = mvc.post().uri("/transactions/do").contentType(MediaType.APPLICATION_JSON)
			.header("Authorization", "Bearer " + token)
			.header("tf-req-uri-root-path", "/ekoicici/v1/marketuat")
			.header("tf-req-uri", "/karza/digilocker-redirection-url")
			.header("tf-req-method", "POST")
			.content("{}")
			.exchange()
			.getResponse()
			.getContentAsString()
			.transform(this::read);
		assertThat(link.at("/data/link").asString()).contains("/v1/dev/digilocker");
		assertThat(step(token, Map.of("interaction_type_id", 552, "mobile", mobile, "token_id",
				link.at("/data/requestId").asString(), "is_consent", "Y", "digilocker_completed", true)).asInt()).isEqualTo(1621);
	}

	@Test
	void distributorAssistsAnAgentWithTheirConsent() throws Exception {
		String agent = "9855500131";
		String dist = login(DIST);
		JsonNode before = post("/authentication/refresh-profile", Map.of("csp_id", agent), dist);
		assertThat(before.has("details")).isFalse();

		JsonNode notYet = post("/", Map.of("interaction_type_id", 521, "mobile", agent, "applicant_type", "0"), dist);
		assertThat(notYet.get("error_code").asString()).isEqualTo("CONSENT_REQUIRED");

		JsonNode sent = post("/", Map.of("interaction_type_id", 194, "csp_id", agent, "merchant_type", ""), dist);
		assertThat(sent.get("response_type_id").asInt()).isEqualTo(873);
		assertThat(post("/", Map.of("interaction_type_id", 197, "csp_id", agent, "otp", "000000"), dist).get("response_type_id")
			.asInt()).isEqualTo(302);
		assertThat(post("/", Map.of("interaction_type_id", 197, "csp_id", agent, "otp", "123456"), dist).get("response_type_id")
			.asInt()).isEqualTo(876);
		assertThat(step(dist, Map.of("interaction_type_id", 521, "mobile", agent, "applicant_type", "0")).asInt()).isEqualTo(1566);

		JsonNode status = post("/authentication/refresh-profile", Map.of("csp_id", agent), dist).at("/details/user_detail");
		assertThat(status.get("onboarding").asInt()).isEqualTo(1);
		assertThat(upload(dist, agent, 1, Map.of(), "file1", "file2").asInt()).isEqualTo(1569);
		assertThat(post("/", Map.of("interaction_type_id", 194, "csp_id", agent), dist).get("response_type_id").asInt())
			.isEqualTo(874);
	}

	@Test
	void adminFunnelCountsAgentsByStage() throws Exception {
		String mobile = "9855500141";
		step(login(mobile), Map.of("interaction_type_id", 521, "mobile", mobile, "applicant_type", "0"));
		String admin = login(ADMIN);
		JsonNode cards = post("/transactions/do", Map.of("interaction_type_id", 683), admin).get("data");
		assertThat(cards.get("totalRecords").asInt()).isPositive();
		assertThat(cards.get("onboarding_funnel").findValuesAsString("status_ids")).containsExactly("10", "11", "12");
		JsonNode rows = post("/transactions/do", Map.of("interaction_type_id", 727, "record_count", 10, "account_status", "10",
				"page_number", 1), admin).at("/data/onboarding_funnel");
		assertThat(rows.findValuesAsString("mobile")).contains(mobile);
	}

	private JsonNode step(String token, Map<String, ?> body) throws Exception {
		JsonNode res = post("/", body, token);
		return res.get("response_type_id");
	}

	private JsonNode upload(String token, String mobile, int docType, Map<String, String> extra, String... files) throws Exception {
		return uploadRaw(token, mobile, docType, extra, files).get("response_type_id");
	}

	private JsonNode uploadRaw(String token, String mobile, int docType, Map<String, String> extra, String... files)
			throws Exception {
		StringBuilder form = new StringBuilder("interaction_type_id=523&doc_type=" + docType + "&mobile=" + mobile);
		extra.forEach((k, v) -> form.append('&').append(k).append('=').append(v));
		var request = mvc.post().uri("/").multipart();
		List<MockMultipartFile> parts = new ArrayList<>();
		for (String f : files) {
			parts.add(new MockMultipartFile(f, f + ".png", "image/png", PNG));
		}
		for (MockMultipartFile part : parts) {
			request = request.file(part);
		}
		return read(request.param("form-data", form.toString()).header("Authorization", "Bearer " + token)
			.exchange().getResponse().getContentAsString());
	}

	private static String encode(String pin, String key) {
		StringBuilder out = new StringBuilder();
		for (char c : pin.toCharArray()) {
			out.append(key.charAt(c - '0'));
		}
		return out.toString();
	}

	private String login(String mobile) throws Exception {
		post("/", Map.of("interaction_type_id", "515", "mobile", mobile, "org_token", orgToken), null);
		return post("/", Map.of("interaction_type_id", "518", "mobile", mobile, "otp", "123456", "org_token", orgToken), null)
			.at("/data/otp_verification_token").asString();
	}

	private JsonNode post(String uri, Map<String, ?> body, String token) throws Exception {
		var request = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		if (token != null) {
			request = request.header("Authorization", "Bearer " + token);
		}
		return read(request.exchange().getResponse().getContentAsString());
	}

	private JsonNode read(String body) {
		return json.readTree(body);
	}
}
