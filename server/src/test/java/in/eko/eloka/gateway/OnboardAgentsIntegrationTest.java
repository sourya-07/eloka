package in.eko.eloka.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.TestcontainersConfiguration;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.network.UserView;
import in.eko.eloka.tenant.NewOrg;
import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.tenant.OrgStatus;
import in.eko.eloka.wallet.WalletApi;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
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

/** P4: "Onboard Agents" by form, demo accounts and spreadsheet upload. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OnboardAgentsIntegrationTest {

	private static final String ADMIN = "9833300003";
	private static final String DIST = "9833300002";
	private static final String RETAILER = "9833300001";

	@Autowired MockMvcTester mvc;
	@Autowired JsonMapper json;
	@Autowired OrgApi orgs;
	@Autowired UserCommands users;
	@Autowired UserDirectory directory;
	@Autowired WalletApi wallets;

	private long orgId;
	private UserView distributor;
	private String orgToken;

	@BeforeAll
	void seed() throws Exception {
		orgId = orgs.createOrg(new NewOrg("Onb Org", "Onb", null, OrgStatus.ACTIVE, List.of("onb.eloka.test"), Map.of())).id();
		users.createUser(user(ADMIN, 24, true, null));
		distributor = users.createUser(user(DIST, 1, false, null));
		users.createUser(user(RETAILER, 2, false, distributor.id()));
		orgToken = post("/", Map.of("interaction_type_id", "677", "sub_domain", "onb", "secret", "test-org-meta-secret"), null)
			.get("org_token").asString();
	}

	@Test
	void adminOnboardsRetailersRowByRow() throws Exception {
		JsonNode result = form(login(ADMIN), "0", List.of(
				Map.of("agent_name", "Asha Stores", "agent_mobile", "9844400001", "dist_mobile", DIST),
				Map.of("agent_name", "Asha Again", "agent_mobile", "9844400001"),
				Map.of("agent_name", "Old Timer", "agent_mobile", RETAILER),
				Map.of("agent_name", "B4d N4me", "agent_mobile", "9844400002"),
				Map.of("agent_name", "Lost Agent", "agent_mobile", "9844400003", "dist_mobile", "9899999999")));
		assertThat(result.get("status").asInt()).isZero();
		assertThat(result.at("/data/processed_records").asInt()).isEqualTo(1);
		assertThat(result.at("/data/failed_count").asInt()).isEqualTo(4);
		JsonNode rows = result.at("/data/csp_list");
		assertThat(rows.findValuesAsString("status")).containsExactly("Success", "Failed", "Failed", "Failed", "Failed");
		assertThat(rows.get(1).get("reason").asString()).contains("Repeated");
		assertThat(rows.get(2).get("reason").asString()).contains("already has an account");
		assertThat(rows.get(4).get("reason").asString()).contains("Distributor not found");
		assertThat(directory.findByMobile(orgId, "9844400001").orElseThrow().parentUserId()).isEqualTo(distributor.id());
	}

	@Test
	void distributorOnboardsRetailersUnderThemselvesOnly() throws Exception {
		String token = login(DIST);
		JsonNode ok = form(token, "0", List.of(Map.of("agent_name", "Under Dist", "agent_mobile", "9844400011")));
		assertThat(ok.at("/data/processed_records").asInt()).isEqualTo(1);
		assertThat(directory.findByMobile(orgId, "9844400011").orElseThrow().parentUserId()).isEqualTo(distributor.id());

		JsonNode denied = form(token, "2", List.of(Map.of("agent_name", "Sub Dist", "agent_mobile", "9844400012")));
		assertThat(denied.get("error_code").asString()).isEqualTo("NOT_ALLOWED");
	}

	@Test
	void retailerCannotOnboard() throws Exception {
		JsonNode denied = form(login(RETAILER), "0", List.of(Map.of("agent_name", "Nope", "agent_mobile", "9844400021")));
		assertThat(denied.get("error_code").asString()).isEqualTo("NOT_ALLOWED");
	}

	@Test
	void demoAccountGetsCreditAndExpiry() throws Exception {
		form(login(ADMIN), "0", List.of(Map.of("agent_name", "Demo Shop", "agent_mobile", "9844400031", "is_demo_account", true,
				"demo_account_validity", "30", "demo_credit_limit", "5000")));
		UserView demo = directory.findByMobile(orgId, "9844400031").orElseThrow();
		assertThat(demo.demo()).isTrue();
		assertThat(demo.demoExpiresOn()).isNotNull();
		assertThat(wallets.accountsOf(orgId, demo.id()).getFirst().balance()).isEqualByComparingTo(new BigDecimal("5000"));
	}

	@Test
	void spreadsheetUploadReadsNumericMobiles() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (Workbook workbook = new Workbook(out, "test", "1.0")) {
			Worksheet sheet = workbook.newWorksheet("Sheet1");
			sheet.value(0, 0, "Mobile");
			sheet.value(0, 1, "Name");
			sheet.value(0, 2, "Distributor Mobile");
			sheet.value(1, 0, 9844400041L);
			sheet.value(1, 1, "Sheet One");
			sheet.value(1, 2, Long.parseLong(DIST));
			sheet.value(2, 0, "9844400042");
			sheet.value(2, 1, "Sheet Two");
		}
		JsonNode result = upload(login(ADMIN), "Bulk_Agent_Onboarding.xlsx", out.toByteArray());
		assertThat(result.at("/data/processed_records").asInt()).isEqualTo(2);
		assertThat(directory.findByMobile(orgId, "9844400041").orElseThrow().parentUserId()).isEqualTo(distributor.id());
	}

	@Test
	void oldXlsFilesAreRejectedClearly() throws Exception {
		JsonNode result = upload(login(ADMIN), "agents.xls", new byte[] { 1, 2, 3 });
		assertThat(result.get("status").asInt()).isEqualTo(1);
		assertThat(result.get("message").asString()).contains(".xlsx");
	}

	private JsonNode form(String token, String applicantType, List<Map<String, Object>> agents) throws Exception {
		var request = mvc.post().uri("/transactions/dojson").contentType(MediaType.APPLICATION_JSON)
			.header("Authorization", "Bearer " + token)
			.header("tf-req-uri-root-path", "/ekoicici/v1")
			.header("tf-req-uri", "/network/agent/multiple_onboarding")
			.header("tf-req-method", "POST")
			.content(json.writeValueAsString(Map.of("applicant_type", applicantType, "CspList", agents)));
		return json.readTree(request.exchange().getResponse().getContentAsString());
	}

	private JsonNode upload(String token, String filename, byte[] bytes) throws Exception {
		var request = mvc.post().uri("/transactions/customupload")
			.multipart()
			.file(new MockMultipartFile("file_name", filename, "application/octet-stream", bytes))
			.param("formdata", "client_ref_id=1&applicant_type=0&source=WLC")
			.header("Authorization", "Bearer " + token)
			.header("tf-req-uri-root-path", "/ekoicici/v1")
			.header("tf-req-uri", "/network/agent/bulk_onboarding")
			.header("tf-req-method", "POST");
		return json.readTree(request.exchange().getResponse().getContentAsString());
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

	private NewUser user(String mobile, int type, boolean admin, UUID parent) {
		return new NewUser(orgId, mobile, "User " + mobile.substring(6), null, type, admin, UserStatus.ACTIVE, null, null, false,
				ShopView.empty(), parent);
	}
}
