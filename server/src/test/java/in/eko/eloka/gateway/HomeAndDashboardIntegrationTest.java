package in.eko.eloka.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import in.eko.eloka.notification.NewNotification;
import in.eko.eloka.notification.NotificationApi;
import in.eko.eloka.tenant.NewOrg;
import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.tenant.OrgStatus;
import in.eko.eloka.wallet.LedgerPosting;
import in.eko.eloka.wallet.WalletApi;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P2: home and dashboard calls, especially that every number stays inside the caller's network. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HomeAndDashboardIntegrationTest {

	private static final String ADMIN = "9822200003";
	private static final String DIST_A = "9822200002";
	private static final String DIST_B = "9822200006";
	private static final String RET_A = "9822200001"; // under DIST_A
	private static final String RET_B = "9822200007"; // under DIST_B

	@Autowired MockMvcTester mvc;
	@Autowired JsonMapper json;
	@Autowired OrgApi orgs;
	@Autowired UserCommands users;
	@Autowired WalletApi wallets;
	@Autowired HistoryApi history;
	@Autowired NotificationApi notifications;

	private String orgToken;

	@BeforeAll
	void seed() throws Exception {
		long org = orgs.createOrg(new NewOrg("Dash Org", "Dash", null, OrgStatus.ACTIVE, List.of("dash.eloka.test"), Map.of())).id();
		users.createUser(user(org, ADMIN, 24, true, null));
		UserView distA = users.createUser(user(org, DIST_A, 1, false, null));
		UserView distB = users.createUser(user(org, DIST_B, 1, false, null));
		UserView retA = users.createUser(user(org, RET_A, 2, false, distA.id()));
		UserView retB = users.createUser(user(org, RET_B, 2, false, distB.id()));
		wallets.post(new LedgerPosting(org, retA.id(), true, new BigDecimal("1500.50"), "T-OPEN", "Opening"));
		Instant now = Instant.now().minus(1, ChronoUnit.HOURS);
		history.record(txn(org, retA, 221, "1000", TxnStatus.SUCCESS, now));
		history.record(txn(org, retA, 225, "400", TxnStatus.FAILED, now));
		history.record(txn(org, retB, 221, "9000", TxnStatus.SUCCESS, now));
		notifications.publish(new NewNotification(org, null, "NORMAL", "Hello all", "Line 1\nLine 2", null, null, null, 2, now));
		notifications.publish(new NewNotification(org, retB.id(), "NORMAL", "Only for B", "x", null, null, null, 2, now));
		orgToken = post("/", Map.of("interaction_type_id", "677", "sub_domain", "dash", "secret", "test-org-meta-secret"), null)
			.get("org_token").asString();
	}

	@Test
	void walletBalanceAndAccountsAreTheCallersOwn() throws Exception {
		String token = login(RET_A);
		assertThat(post("/", Map.of("interaction_type_id", 9, "user_code", "99029899"), token).at("/data/balance").decimalValue())
			.isEqualByComparingTo("1500.50");
		JsonNode accounts = post("/", Map.of("interaction_type_id", "151"), token).at("/data/account_detail/account_list");
		assertThat(accounts).hasSize(1);
		assertThat(accounts.get(0).get("type_id").asInt()).isEqualTo(1);
	}

	@Test
	void recentTransactionsOnlyShowTheCallersOwn() throws Exception {
		JsonNode rows = post("/transactions/do", Map.of("interaction_type_id", 154, "start_index", 0, "limit", 10), login(RET_A))
			.at("/data/transaction_list");
		assertThat(rows).hasSize(2);
		assertThat(rows.findValuesAsString("tx_name")).containsOnly("Money Transfer", "AePS Cashout");
	}

	@Test
	void menuIsABareArrayWithRequiredGroups() throws Exception {
		JsonNode menu = post("/transactions/wlc", Map.of(), login(RET_A));
		assertThat(menu.isArray()).isTrue();
		assertThat(menu.findValuesAsString("group_interaction_ids")).contains("64,249", "640,736");
	}

	@Test
	void activeCountsAreScoped() throws Exception {
		assertThat(post("/", Map.of("interaction_type_id", 818), login(ADMIN)).at("/data/dashboard_object/totalActiveData/2/totalcount").asString())
			.isEqualTo("2");
		JsonNode distributor = post("/", Map.of("interaction_type_id", 818), login(DIST_A)).at("/data/dashboard_object/totalActiveData");
		assertThat(distributor.at("/2/totalcount").asString()).isEqualTo("1");
		assertThat(distributor.has("1")).isFalse();
		assertThat(post("/", Map.of("interaction_type_id", 818), login(RET_A)).at("/data/dashboard_object/totalActiveData").isEmpty()).isTrue();
	}

	@Test
	void businessDashboardIsScopedToTheNetwork() throws Exception {
		Map<String, Object> request = Map.of("interaction_type_id", 682, "requestPayload",
				Map.of("products_overview", range(), "gtv_top_merchants", range(), "success_rate", range()));
		JsonNode admin = post("/", request, login(ADMIN)).at("/data/dashboard_object");
		assertThat(admin.at("/products_overview/gtv/amount").decimalValue()).isEqualByComparingTo("10000");
		assertThat(admin.at("/gtv_top_merchants")).hasSize(2);

		JsonNode distributor = post("/", request, login(DIST_A)).at("/data/dashboard_object");
		assertThat(distributor.at("/products_overview/gtv/amount").decimalValue()).isEqualByComparingTo("1000");
		assertThat(distributor.at("/products_overview/failedCases/failedCases").asLong()).isEqualTo(1);
		assertThat(distributor.at("/gtv_top_merchants/0/cellNumber").asString()).isEqualTo(RET_A);
		assertThat(distributor.at("/successRate/221/successCount").asLong()).isEqualTo(1);

		assertThat(post("/", request, login(RET_A)).get("error_code").asString()).isEqualTo("NOT_ALLOWED");
	}

	@Test
	void notificationsShowOrgWideAndOwnOnlyAndTrackReceipts() throws Exception {
		String tokenA = login(RET_A);
		JsonNode list = post("/transactions/do", Map.of("interaction_type_id", 10010), tokenA).at("/data/notifications");
		assertThat(list.findValuesAsString("title")).containsExactly("Hello all");
		assertThat(list.get(0).get("notification_type").asInt()).isZero();
		assertThat(list.get(0).get("delivery_status").asInt()).isZero();

		long id = list.get(0).get("id").asLong();
		post("/transactions/do", Map.of("interaction_type_id", 10023, "notification_id", id, "delivery_status", 2), tokenA);
		post("/transactions/do", Map.of("interaction_type_id", 10012, "notification_id", id, "notification_status", 1), tokenA);
		JsonNode after = post("/transactions/do", Map.of("interaction_type_id", 10010), tokenA).at("/data/notifications/0");
		assertThat(after.get("read").asInt()).isEqualTo(1);
		assertThat(after.get("delivery_status").asInt()).isEqualTo(2);
	}

	@Test
	void earningsAndProductsAnswer() throws Exception {
		JsonNode earnings = post("/transactions/do", Map.of("interaction_type_id", 623), login(RET_A)).get("data");
		assertThat(earnings.has("this_month")).isTrue();
		JsonNode products = post("/", Map.of("interaction_type_id", 1044), login(ADMIN)).at("/param_attributes/list_elements");
		assertThat(products.findValuesAsString("tx_typeid")).contains("221", "225", "244");
	}

	private static Map<String, Object> range() {
		return Map.of("datefrom", "2020-01-01 00:00:00", "dateto", "2099-12-31 23:59:59");
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

	private static NewTxn txn(long org, UserView user, int type, String amount, TxnStatus status, Instant at) {
		return new NewTxn(org, user.id(), user.userCode(), null, type, type == 221 ? "Money Transfer" : "AePS Cashout", true,
				new BigDecimal(amount), BigDecimal.TEN, BigDecimal.ONE, status, "Customer", "9811111111", at, Map.of());
	}
}
