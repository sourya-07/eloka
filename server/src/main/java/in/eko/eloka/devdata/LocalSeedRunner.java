package in.eko.eloka.devdata;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.agentonboarding.AgentOnboardingApi;
import in.eko.eloka.agentonboarding.KycStep;
import in.eko.eloka.history.HistoryApi;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.network.UserView;
import in.eko.eloka.notification.NewNotification;
import in.eko.eloka.notification.NotificationApi;
import in.eko.eloka.shared.config.UnsafeInProduction;
import in.eko.eloka.tenant.NewOrg;
import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.tenant.OrgStatus;
import in.eko.eloka.tenant.OrgView;
import in.eko.eloka.wallet.LedgerPosting;
import in.eko.eloka.wallet.WalletAccountView;
import in.eko.eloka.wallet.WalletApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Creates the dummy orgs and users from {@code devdata/local-seed.json} through the normal
 * module APIs (so encryption, codes and audit behave exactly as in production).
 * Idempotent: existing orgs/users are left alone.
 */
@Component
@Profile("local")
class LocalSeedRunner implements ApplicationRunner, UnsafeInProduction {

	private static final Logger log = LoggerFactory.getLogger(LocalSeedRunner.class);

	private final OrgApi orgs;
	private final UserDirectory userDirectory;
	private final UserCommands userCommands;
	private final WalletApi wallets;
	private final HistoryApi history;
	private final NotificationApi notifications;
	private final AgentOnboardingApi onboarding;
	private final in.eko.eloka.identity.IdentityApi identity;
	private final JsonMapper json;
	private final java.time.Clock clock;

	LocalSeedRunner(OrgApi orgs, UserDirectory userDirectory, UserCommands userCommands, WalletApi wallets,
			HistoryApi history, NotificationApi notifications, AgentOnboardingApi onboarding,
			in.eko.eloka.identity.IdentityApi identity, JsonMapper json, java.time.Clock clock) {
		this.identity = identity;
		this.orgs = orgs;
		this.userDirectory = userDirectory;
		this.userCommands = userCommands;
		this.wallets = wallets;
		this.history = history;
		this.notifications = notifications;
		this.onboarding = onboarding;
		this.json = json;
		this.clock = clock;
	}

	@Override
	public String unsafeReason() {
		return "LocalSeedRunner (dummy users) is active";
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		seed();
	}

	/** Loads the seed file and creates whatever is missing. */
	void seed() throws IOException {
		LocalSeed seed;
		try (InputStream in = new ClassPathResource("devdata/local-seed.json").getInputStream()) {
			seed = json.readValue(in, LocalSeed.class);
		}
		int created = 0;
		if (seed.staff() != null) {
			for (LocalSeed.SeedStaff member : seed.staff()) {
				java.util.Set<in.eko.eloka.identity.StaffRole> roles = java.util.EnumSet.noneOf(in.eko.eloka.identity.StaffRole.class);
				member.roles().forEach(role -> roles.add(in.eko.eloka.identity.StaffRole.valueOf(role)));
				identity.saveStaff(member.email(), member.name(), member.mobile(), roles, true);
			}
		}
		for (LocalSeed.SeedOrg seedOrg : seed.orgs()) {
			boolean newOrg = orgs.findByHostname(orgs.hostnameForSubdomain(seedOrg.subdomains().getFirst())).isEmpty();
			OrgView org = newOrg ? orgs.createOrg(toNewOrg(seedOrg))
					: orgs.findByHostname(orgs.hostnameForSubdomain(seedOrg.subdomains().getFirst())).orElseThrow();
			if (newOrg) {
				seedNotifications(org.id(), seedOrg.notifications());
			}
			// Parents first: users without a parent, then the ones under them.
			List<LocalSeed.SeedUser> ordered = new ArrayList<>(seedOrg.users());
			ordered.sort(java.util.Comparator.comparing(user -> user.parent() != null));
			for (LocalSeed.SeedUser user : ordered) {
				if (userDirectory.findByMobile(org.id(), user.mobile()).isEmpty()) {
					java.util.UUID parentId = user.parent() == null ? null
							: userDirectory.findByUserCode(org.id(), user.parent()).orElseThrow().id();
					UserView view = userCommands.createUser(toNewUser(org.id(), user, parentId));
					seedMoney(view, user);
					seedKyc(view, user);
					created++;
				}
			}
		}
		log.info("Local seed ready ({} new users). Log in with 9000000001/2/3/5 and OTP 123456.", created);
	}

	/** Opening balance plus ~90 days of dummy transactions for agents that have them. */
	private void seedMoney(UserView user, LocalSeed.SeedUser seed) {
		WalletAccountView account = wallets.ensureEvalueAccount(user.orgId(), user.id(), user.userCode());
		if (seed.balance() != null && seed.balance().signum() > 0) {
			wallets.post(new LedgerPosting(user.orgId(), user.id(), true, seed.balance(), "SEED-OPENING", "Opening balance"));
		}
		if (seed.transactions()) {
			DummyTransactions.forUser(user, account.id(), clock).forEach(history::record);
		}
	}

	/** Seeded agents are onboarded, except the ones whose seed lists only some finished steps. */
	private void seedKyc(UserView user, LocalSeed.SeedUser seed) {
		java.util.Set<KycStep> done = seed.kycDone() == null ? java.util.EnumSet.allOf(KycStep.class)
				: seed.kycDone().stream().map(KycStep::valueOf).collect(java.util.stream.Collectors.toSet());
		onboarding.completeSteps(user.orgId(), user.id(), done);
	}

	private void seedNotifications(long orgId, List<LocalSeed.SeedNotification> items) {
		java.time.Instant now = java.time.Instant.now(clock);
		for (int i = 0; i < items.size(); i++) {
			LocalSeed.SeedNotification n = items.get(i);
			notifications.publish(new NewNotification(orgId, null, n.type(), n.title(), n.body(), null, n.link(),
					n.linkLabel(), n.priority(), now.minusSeconds(3600L * (items.size() - i))));
		}
	}

	private NewOrg toNewOrg(LocalSeed.SeedOrg org) {
		List<String> hostnames = new ArrayList<>();
		org.subdomains().forEach(label -> hostnames.add(orgs.hostnameForSubdomain(label)));
		hostnames.addAll(org.extraHostnames());
		Map<String, String> metadata = new LinkedHashMap<>();
		org.metadata().forEach((key, value) -> metadata.put(key, json.writeValueAsString(value)));
		return new NewOrg(org.name(), org.appName(), org.logoUrl(), OrgStatus.ACTIVE, hostnames, metadata);
	}

	private static NewUser toNewUser(long orgId, LocalSeed.SeedUser user, java.util.UUID parentId) {
		LocalSeed.SeedShop shop = user.shop();
		ShopView shopView = shop == null ? null
				: new ShopView(shop.shopName(), shop.shopType(), shop.addressLine(), null, shop.city(), shop.state(),
						shop.pincode(), "India", "Owned", null, null);
		return new NewUser(orgId, user.mobile(), user.name(), user.email(), user.userType(), user.orgAdmin(),
				UserStatus.ACTIVE, user.code(), user.joined(), false, shopView, parentId);
	}
}
