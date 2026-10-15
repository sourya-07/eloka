package in.eko.eloka.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.TestcontainersConfiguration;
import in.eko.eloka.history.HistoryApi;
import in.eko.eloka.history.NewTxn;
import in.eko.eloka.history.TxnStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P3: network list/profile/actions, history views and exports, own profile edits; all scoped. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkIntegrationTest {

	private static final String V1 = "/ekoicici/v1";
	private static final String ADMIN = "9833300003";
	private static final String DIST_A = "9833300002";
	private static final String DIST_B = "9833300006";
	private static final String RET_A1 = "9833300001";
	private static final String RET_A2 = "9833300011";
	private static final String RET_B1 = "9833300021";
	private static final String DEMO_A = "9833300031";
	private static final String PROMOTE_ME = "9833300041"; // only used by the role-change test

	@Autowired MockMvcTester mvc;
	@Autowired JsonMapper json;
	@Autowired OrgApi orgs;
	@Autowired UserCommands users;
	@Autowired HistoryApi history;

	private String orgToken;
	private UserView retA1;
	private UserView retB1;
	private UserView distB;

	@BeforeAll
	void seed() throws Exception {
		long org = orgs.createOrg(new NewOrg("Net Org", "Net", null, OrgStatus.ACTIVE, List.of("net.eloka.test"), Map.of())).id();
		users.createUser(user(org, ADMIN, 24, true, null, false));
		UserView distA = users.createUser(user(org, DIST_A, 1, false, null, false));
		distB = users.createUser(user(org, DIST_B, 1, false, null, false));
		retA1 = users.createUser(user(org, RET_A1, 2, false, distA.id(), false));
		users.createUser(user(org, RET_A2, 2, false, distA.id(), false));
		retB1 = users.createUser(user(org, RET_B1, 2, false, distB.id(), false));
		users.createUser(user(org, DEMO_A, 2, false, distA.id(), true));
		users.createUser(user(org, PROMOTE_ME, 2, false, distA.id(), false));
		Instant at = Instant.now().minus(2, ChronoUnit.HOURS);
		history.record(txn(org, retA1, "500", at));
		history.record(txn(org, retB1, "700", at));
		orgToken = post("/", Map.of("interaction_type_id", "677", "sub_domain", "net", "secret", "test-org-meta-secret"), null)
			.get("org_token").asString();
	}

	@Test
	void networkListIsScoped() throws Exception {
		// Other tests move or remove users, so check membership rather than exact counts.
		JsonNode admin = tf("GET", "/network/agents", Map.of("record_count", 50, "page_number", 1), login(ADMIN)).get("data");
		assertThat(admin.get("agent_details").findValuesAsString("agent_mobile")).contains(DIST_A, DIST_B, RET_B1, RET_A2)
			.doesNotContain(ADMIN);
		JsonNode distributor = tf("GET", "/network/agents", Map.of("record_count", 50, "page_number", 1), login(DIST_A)).get("data");
		assertThat(distributor.get("agent_details").findValuesAsString("agent_mobile")).contains(RET_A2)
			.doesNotContain(RET_B1, DIST_A, DIST_B, ADMIN);
		assertThat(distributor.get("totalRecords").asLong()).isLessThan(admin.get("totalRecords").asLong());
		JsonNode retailers = tf("GET", "/network/agents", Map.of("agentType", 2, "record_count", 50), login(ADMIN)).get("data");
		assertThat(retailers.get("agent_details").findValuesAsString("user_type_id")).containsOnly("2");
	}

	@Test
	void agentProfileLookupIsScoped() throws Exception {
		JsonNode found = tf("GET", "/network/agents", Map.of("record_count", 1, "search_value", RET_A1), login(DIST_A));
		assertThat(found.get("response_type_id").asInt()).isEqualTo(1827);
		assertThat(found.at("/data/agent_details/0/account_status_id").asInt()).isEqualTo(16);
		JsonNode hidden = tf("GET", "/network/agents", Map.of("record_count", 1, "search_value", RET_B1), login(DIST_A));
		assertThat(hidden.get("error_code").asString()).isEqualTo("USER_NOT_FOUND");
	}

	@Test
	void retailersCannotUseNetworkCalls() throws Exception {
		assertThat(tf("GET", "/network/agents", Map.of(), login(RET_B1)).get("error_code").asString()).isEqualTo("NOT_ALLOWED");
	}

	@Test
	void statusChangeBlocksLoginAndStaysInScope() throws Exception {
		String dist = login(DIST_A);
		JsonNode result = tf("POST", "/network/agents/updateStatus",
				Map.of("csp_code", retA1.userCode(), "agentAccountStatus", 18, "updateStatusNote", "test"), dist);
		assertThat(result.get("response_type_id").asInt()).isEqualTo(1831);
		post("/", Map.of("interaction_type_id", "515", "mobile", RET_A1, "org_token", orgToken), null);
		assertThat(post("/", Map.of("interaction_type_id", "518", "mobile", RET_A1, "otp", "123456", "org_token", orgToken), null)
			.get("accountInactive").asBoolean()).isTrue();
		tf("POST", "/network/agents/updateStatus", Map.of("csp_code", retA1.userCode(), "agentAccountStatus", 16), dist);
		assertThat(tf("POST", "/network/agents/updateStatus", Map.of("csp_code", retB1.userCode(), "agentAccountStatus", 18), dist)
			.get("error_code").asString()).isEqualTo("USER_NOT_FOUND");
	}

	@Test
	void roleChangesAreForAdminsAndMoveTheHierarchy() throws Exception {
		assertThat(tf("PUT", "/network/agents/profile/changeRole/promotecsps", Map.of("agent_mobile", PROMOTE_ME), login(DIST_A))
			.get("error_code").asString()).isEqualTo("NOT_ALLOWED");
		String admin = login(ADMIN);
		assertThat(tf("PUT", "/network/agents/profile/changeRole/promotecsps", Map.of("operation_type", 1, "agent_mobile", PROMOTE_ME),
				admin).get("response_type_id").asInt()).isEqualTo(1967);
		assertThat(tf("PUT", "/network/agents/profile/changeRole/transfercsps",
				Map.of("scspTo", distB.userCode(), "selectedTransferredCSPsList", retA1.userCode()), admin)
			.get("response_type_id").asInt()).isEqualTo(1872);
		JsonNode underB = tf("GET", "/network/agent-list", Map.of("usertype", 2), login(DIST_B)).at("/data/csp_list");
		assertThat(underB.findValuesAsString("mobile")).contains(RET_A1, RET_B1);
		assertThat(underB.findValuesAsString("parent_user_code")).containsOnly(distB.userCode());
		// move back for the other tests
		tf("PUT", "/network/agents/profile/changeRole/transfercsps",
				Map.of("scspTo", codeOf(DIST_A, admin), "selectedTransferredCSPsList", retA1.userCode()), admin);
	}

	@Test
	void demoUsersCanBeRemovedButRealOnesCannot() throws Exception {
		String dist = login(DIST_A);
		String demoCode = codeOf(DEMO_A, dist);
		assertThat(tf("POST", "/network/agent", Map.of("csp_code", retA1.userCode()), dist).get("error_code").asString())
			.isEqualTo("NOT_A_DEMO_USER");
		assertThat(tf("POST", "/network/agent", Map.of("csp_code", demoCode), dist).get("status").asInt()).isZero();
		assertThat(tf("GET", "/network/agents", Map.of("record_count", 1, "search_value", DEMO_A), dist).get("error_code").asString())
			.isEqualTo("USER_NOT_FOUND");
	}

	@Test
	void historyViewsAreScoped() throws Exception {
		JsonNode adminNetwork = post("/", Map.of("interaction_type_id", 154, "isNetworkTransactionHistory", 1, "start_index", 0,
				"limit", 25), login(ADMIN)).at("/data/transaction_list");
		assertThat(adminNetwork.findValuesAsString("agent_cell")).contains(RET_A1, RET_B1);
		JsonNode distNetwork = post("/", Map.of("interaction_type_id", 154, "isNetworkTransactionHistory", 1, "start_index", 0,
				"limit", 25), login(DIST_B)).at("/data/transaction_list");
		assertThat(distNetwork.findValuesAsString("agent_cell")).containsOnly(RET_B1);
		assertThat(post("/", Map.of("interaction_type_id", 154, "isNetworkTransactionHistory", 1, "csp_code", retA1.userCode()),
				login(DIST_B)).get("error_code").asString()).isEqualTo("USER_NOT_FOUND");
		JsonNode own = post("/", Map.of("interaction_type_id", 154, "isNetworkTransactionHistory", 0), login(RET_B1))
			.at("/data/transaction_list");
		assertThat(own).hasSize(1);
		assertThat(post("/", Map.of("interaction_type_id", 154, "customer_mobile", "9811111111"), login(RET_B1))
			.at("/data/transaction_list")).hasSize(1);
	}

	@Test
	void exportsReturnFiles() throws Exception {
		JsonNode xlsx = post("/", Map.of("interaction_type_id", 183, "isNetworkTransactionHistory", 1, "reporttype", "xlsx"),
				login(ADMIN)).get("file");
		assertThat(xlsx.get("name").asString()).isEqualTo("network_transactions.xlsx");
		assertThat(Base64.getDecoder().decode(xlsx.get("blob").asString())).startsWith('P', 'K');
		JsonNode pdf = post("/", Map.of("interaction_type_id", 183, "reporttype", "pdf"), login(RET_B1)).get("file");
		assertThat(new String(Base64.getDecoder().decode(pdf.get("blob").asString()), 0, 5)).isEqualTo("%PDF-");

		assertThat(mvc.post().uri("/reports/agent-subnetwork?reporttype=xlsx")).hasStatus(401);
		MvcTestResult report = mvc.post().uri("/reports/agent-subnetwork?reporttype=xlsx")
			.header("Authorization", "Bearer " + login(ADMIN)).exchange();
		assertThat(report).hasStatusOk();
		assertThat(report.getResponse().getContentAsByteArray()).startsWith('P', 'K');
	}

	@Test
	void ownProfileEditsShowUp() throws Exception {
		String token = login(RET_B1);
		post("/", Map.of("interaction_type_id", 401, "section", "personal_detail", "dob", 15081990, "gender", "Female",
				"marital_status", "Single", "qualification", "Graduate"), token);
		post("/", Map.of("interaction_type_id", 401, "section", "shop_detail", "shop_name", "New Shop", "city", "Mysuru",
				"shop_address_state", "Karnataka", "pincode", "570001"), token);
		JsonNode profile = post("/", Map.of("interaction_type_id", "151"), token).get("data");
		assertThat(profile.at("/personal_detail/dob").asString()).isEqualTo("1990-08-15");
		assertThat(profile.at("/shop_detail/shop_name").asString()).isEqualTo("New Shop");
		assertThat(post("/", Map.of("interaction_type_id", 401, "section", "shop_detail", "pincode", "12"), token)
			.get("error_code").asString()).isEqualTo("VALIDATION_FAILED");
	}

	private String codeOf(String mobile, String token) throws Exception {
		return tf("GET", "/network/agents", Map.of("record_count", 1, "search_value", mobile), token)
			.at("/data/agent_details/0/user_id").asString();
	}

	private JsonNode tf(String method, String uri, Map<String, ?> body, String token) throws Exception {
		MvcTestResult result = mvc.post().uri("/transactions/dojson").contentType(MediaType.APPLICATION_JSON)
			.header("tf-req-uri-root-path", V1).header("tf-req-uri", uri).header("tf-req-method", method)
			.header("Authorization", "Bearer " + token).content(json.writeValueAsString(body)).exchange();
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

	private static NewUser user(long org, String mobile, int type, boolean admin, UUID parent, boolean demo) {
		return new NewUser(org, mobile, "User " + mobile.substring(6), null, type, admin, UserStatus.ACTIVE, null, null, demo,
				ShopView.empty(), parent);
	}

	private static NewTxn txn(long org, UserView user, String amount, Instant at) {
		return new NewTxn(org, user.id(), user.userCode(), null, 221, "Money Transfer", true, new BigDecimal(amount),
				BigDecimal.TEN, BigDecimal.ONE, TxnStatus.SUCCESS, "Customer", "9811111111", at, Map.of());
	}
}
