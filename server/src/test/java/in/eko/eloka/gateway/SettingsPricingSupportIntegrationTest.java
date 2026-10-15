package in.eko.eloka.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.TestcontainersConfiguration;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.network.UserView;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P4: pricing, business settings, agreement signing, support tickets. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettingsPricingSupportIntegrationTest {

	private static final String ADMIN = "9844400003";
	private static final String DIST = "9844400002";
	private static final String RET = "9844400001";
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	@Autowired MockMvcTester mvc;
	@Autowired JsonMapper json;
	@Autowired OrgApi orgs;
	@Autowired UserCommands users;

	private String orgToken;
	private UserView dist;
	private UserView ret;

	@BeforeAll
	void seed() throws Exception {
		long org = orgs.createOrg(new NewOrg("P4 Org", "P4", null, OrgStatus.ACTIVE, List.of("p4.eloka.test"), Map.of())).id();
		users.createUser(user(org, ADMIN, 24, true, null));
		dist = users.createUser(user(org, DIST, 1, false, null));
		ret = users.createUser(user(org, RET, 2, false, dist.id()));
		orgToken = post("/", Map.of("interaction_type_id", "677", "sub_domain", "p4", "secret", "test-org-meta-secret"), null)
			.get("org_token").asString();
	}

	@Test
	void pricingTreeSetCurrentAndAgentCommission() throws Exception {
		String admin = login(ADMIN);
		assertThat(post("/transactions/dojson", Map.of("interaction_type_id", 837), admin).at("/data/product_list")).isNotEmpty();

		// AePS agent commission (product 111), slab 1000-10000, 0.3% for everyone
		JsonNode set = post("/", Map.of("interaction_type_id", 838, "operation", 1, "product_id", 111, "operation_type", "3",
				"pricing_type", "0", "actual_pricing", "0.3", "min_slab_amount", 1000, "max_slab_amount", 10000), admin);
		assertThat(set.get("status").asInt()).isZero();
		JsonNode current = post("/", Map.of("interaction_type_id", 1046, "product_id", 111, "operation_type", "3",
				"min_slab_amount", 1000, "max_slab_amount", 10000), admin);
		assertThat(current.at("/param_attributes/type").asString()).isEqualTo("0");
		assertThat(current.at("/param_attributes/value").asString()).isEqualTo("0.3");

		// More specific rule for this retailer wins
		post("/", Map.of("interaction_type_id", 838, "operation", 1, "product_id", 111, "operation_type", "1", "CspList",
				ret.userCode(), "pricing_type", "1", "actual_pricing", "10", "min_slab_amount", 1000, "max_slab_amount", 10000), admin);
		JsonNode rows = tfGet("/network/agent/pricing_commission", login(RET)).at("/data/pricing_commission_data");
		JsonNode row = rows.findParents("product_id").stream().filter(r -> r.get("product_id").asInt() == 111).findFirst().orElseThrow();
		assertThat(row.get("calc_type").asInt()).isEqualTo(2);
		assertThat(row.get("value").asString()).isEqualTo("10");
		assertThat(row.get("tx_type_id").asInt()).isEqualTo(225);
	}

	@Test
	void pricingIsValidatedAndAdminOnly() throws Exception {
		String admin = login(ADMIN);
		JsonNode tooHigh = post("/", Map.of("interaction_type_id", 838, "product_id", 111, "operation_type", "3",
				"pricing_type", "0", "actual_pricing", "5", "min_slab_amount", 1000, "max_slab_amount", 10000), admin);
		assertThat(tooHigh.get("error_code").asString()).isEqualTo("VALIDATION_FAILED");
		JsonNode retailerAsNetwork = post("/", Map.of("interaction_type_id", 838, "product_id", 111, "operation_type", "2",
				"CspList", ret.userCode(), "pricing_type", "0", "actual_pricing", "0.1", "min_slab_amount", 1000,
				"max_slab_amount", 10000), admin);
		assertThat(retailerAsNetwork.at("/invalid_params/CspList").asString()).contains("distributors");
		assertThat(post("/", Map.of("interaction_type_id", 838, "product_id", 111, "actual_pricing", "1"), login(DIST))
			.get("error_code").asString()).isEqualTo("NOT_ALLOWED");
		JsonNode export = post("/", Map.of("interaction_type_id", 756), admin).at("/data/file");
		assertThat(Base64.getDecoder().decode(export.get("blob").asString())).startsWith('P', 'K');
	}

	@Test
	void businessToggles() throws Exception {
		String admin = login(ADMIN);
		assertThat(post("/", Map.of("interaction_type_id", 737, "operation", 0), admin).at("/data/otp_verification_token").asString())
			.isEqualTo("0");
		post("/", Map.of("interaction_type_id", 737, "operation", 2, "otp_verification_token", "1"), admin);
		assertThat(post("/", Map.of("interaction_type_id", 737, "operation", 0), admin).at("/data/otp_verification_token").asString())
			.isEqualTo("1");
		post("/", Map.of("interaction_type_id", 737, "operation", 4, "cdm_charges", "0"), admin);
		assertThat(post("/", Map.of("interaction_type_id", 737, "operation", 5, "service_code", 320), admin)
			.at("/data/cdm_charges").asString()).isEqualTo("0");
		assertThat(post("/", Map.of("interaction_type_id", 726, "source_flag", "2", "communication", 2, "CspList",
				dist.userCode()), admin).get("status").asInt()).isZero();
	}

	@Test
	void signatureEmailAndPincode() throws Exception {
		String admin = login(ADMIN);
		MvcTestResult upload = mvc.post().uri("/transactions/customupload").multipart()
			.file(new MockMultipartFile("file1", "sign.png", "image/png", PNG))
			.param("formdata", "doc_type=19&intent_id=4&authorized_signatory_name=Arjun Admin")
			.header("tf-req-uri-root-path", "/ekoicici/v2").header("tf-req-uri", "/network/agreement/upload-doc-sign-config")
			.header("tf-req-method", "POST").header("Authorization", "Bearer " + admin).exchange();
		assertThat(json.readTree(upload.getResponse().getContentAsString()).get("status").asInt()).isZero();
		JsonNode recent = post("/transactions/do", Map.of(), admin, "/ekoicici/v2", "/network/agreement/recent-signature");
		assertThat(Base64.getDecoder().decode(recent.at("/data/code").asString())).isEqualTo(PNG);

		post("/", Map.of("interaction_type_id", 553, "email", "Owner@Example.com", "intent_id", 16), admin);
		assertThat(post("/", Map.of("interaction_type_id", 555, "email", "owner@example.com", "intent_id", 16, "otp", "000000"), admin)
			.get("status").asInt()).isEqualTo(1);
		post("/", Map.of("interaction_type_id", 554, "email", "owner@example.com", "intent_id", 16), admin);
		assertThat(post("/", Map.of("interaction_type_id", 555, "email", "owner@example.com", "intent_id", 16, "otp", "123456"), admin)
			.get("status").asInt()).isZero();
		assertThat(post("/", Map.of("interaction_type_id", "151"), admin).at("/data/user_detail/business_email").asString())
			.isEqualTo("owner@example.com");

		JsonNode pin = post("/", Map.of("interaction_type_id", 353, "pincode", "560001"), admin);
		assertThat(pin.get("dependent_params").findValuesAsString("value")).contains("Karnataka");
	}

	@Test
	void supportTicketLifecycleAndVisibility() throws Exception {
		String retailer = login(RET);
		assertThat(post("/", Map.of("interaction_type_id", 10022, "tx_typeid", "-1"), retailer).at("/data/issuetype_list")).isNotEmpty();
		JsonNode raised = post("/transactions/do", Map.of("interaction_type_id", "10000", "summary", "Wallet balance mismatch",
				"description", "<p>Balance is <b>wrong</b></p>", "comment", "Balance shows 0", "category", "Wallet"), retailer);
		assertThat(raised.at("/data/feedback_ticket_id").asString()).startsWith("ELK-");

		MvcTestResult withFile = mvc.post().uri("/transactions/upload").multipart()
			.file(new MockMultipartFile("file_1_screenshot", "s.png", "image/png", PNG))
			.param("formdata", "interaction_type_id=10000&summary=Something else&comment=see file")
			.header("Authorization", "Bearer " + retailer).exchange();
		assertThat(json.readTree(withFile.getResponse().getContentAsString()).get("status").asInt()).isZero();

		JsonNode mine = post("/transactions/do", Map.of("interaction_type_id", 692), retailer).at("/data/csp_list");
		assertThat(mine).hasSize(2);
		String admin = login(ADMIN);
		JsonNode all = post("/transactions/do", Map.of("interaction_type_id", 692), admin).at("/data/csp_list");
		assertThat(all.findValuesAsString("contactName")).contains(ret.name());
		String id = all.get(0).get("id").asString();

		JsonNode comments = post("/transactions/do", Map.of("interaction_type_id", 10016, "feedback_ticket_id", id), admin)
			.at("/data/comment_list");
		assertThat(comments.get(0).get("comment").asString()).startsWith("**Agent:**");
		post("/transactions/do", Map.of("interaction_type_id", 10017, "feedback_ticket_id", id, "comment", "Looking into it"), admin);
		post("/transactions/do", Map.of("interaction_type_id", 10018, "feedback_ticket_id", id), admin);
		assertThat(post("/transactions/do", Map.of("interaction_type_id", 10020, "feedback_ticket_id", id), admin)
			.get("response_status_id").asInt()).isZero();
		assertThat(post("/transactions/do", Map.of("interaction_type_id", 10017, "feedback_ticket_id", id, "comment", "x"), admin)
			.get("error_code").asString()).isEqualTo("TICKET_CLOSED");
		assertThat(post("/transactions/do", Map.of("interaction_type_id", 10016, "feedback_ticket_id", id), login(DIST))
			.get("error_code").asString()).isEqualTo("TICKET_NOT_FOUND");
	}

	private JsonNode tfGet(String uri, String token) throws Exception {
		return post("/transactions/do", Map.of(), token, "/ekoicici/v1", uri, "GET");
	}

	private JsonNode post(String uri, Map<String, ?> body, String token, String root, String tfUri) throws Exception {
		return post(uri, body, token, root, tfUri, "GET");
	}

	private JsonNode post(String uri, Map<String, ?> body, String token, String root, String tfUri, String method) throws Exception {
		MvcTestResult result = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).header("tf-req-uri-root-path", root)
			.header("tf-req-uri", tfUri).header("tf-req-method", method).header("Authorization", "Bearer " + token)
			.content(json.writeValueAsString(body)).exchange();
		return json.readTree(result.getResponse().getContentAsString());
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
		return json.readTree(request.exchange().getResponse().getContentAsString());
	}

	private static NewUser user(long org, String mobile, int type, boolean admin, UUID parent) {
		return new NewUser(org, mobile, "User " + mobile.substring(6), null, type, admin, UserStatus.ACTIVE, null, null, false,
				ShopView.empty(), parent);
	}
}
