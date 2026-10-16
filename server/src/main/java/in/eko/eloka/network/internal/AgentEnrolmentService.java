package in.eko.eloka.network.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.network.AgentEnrolment;
import in.eko.eloka.network.AgentSignup;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.OnboardResult;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.network.UserTypes;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.DomainException;
import in.eko.eloka.shared.error.ForbiddenException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates agents row by row. Each row runs in its own transaction, so one bad row (duplicate
 * mobile, unknown distributor) never undoes the others.
 */
@Service
class AgentEnrolmentService implements AgentEnrolment {

	private static final String NAME_PATTERN = "[A-Za-z][A-Za-z .']{1,99}";

	private final AppUserRepository users;
	private final UserCommands userCommands;
	private final BlindIndex index;
	private final TransactionTemplate transactions;
	private final AuditLog audit;
	private final Clock clock;

	AgentEnrolmentService(AppUserRepository users, UserCommands userCommands, BlindIndex index,
			TransactionTemplate transactions, AuditLog audit, Clock clock) {
		this.users = users;
		this.userCommands = userCommands;
		this.index = index;
		this.transactions = transactions;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	public List<OnboardResult> onboard(Caller caller, int userType, List<AgentSignup> agents) {
		AppUser self = users.findByIdAndOrgId(caller.userId(), caller.orgId())
			.orElseThrow(() -> new ForbiddenException("NOT_ALLOWED", "You are not allowed to do this."));
		checkAllowed(caller, self, userType);
		if (agents.isEmpty()) {
			throw ValidationException.field("CspList", "Add at least one agent");
		}
		if (agents.size() > MAX_ROWS) {
			throw ValidationException.field("CspList", "At most " + MAX_ROWS + " agents at a time");
		}

		Set<String> seen = new HashSet<>();
		List<OnboardResult> results = new ArrayList<>();
		for (AgentSignup agent : agents) {
			String name = agent.name() == null ? "" : agent.name().trim();
			String mobile = agent.mobile() == null ? "" : agent.mobile().trim();
			try {
				String normalized = Mobiles.normalize(mobile);
				if (!name.matches(NAME_PATTERN)) {
					throw ValidationException.field("agent_name", "Name can only contain letters and spaces");
				}
				if (!seen.add(normalized)) {
					throw ValidationException.field("agent_mobile", "Repeated in this list");
				}
				UUID parentId = parentFor(caller, self, userType, agent);
				UserView created = transactions.execute(status -> create(caller, userType, agent, name, normalized, parentId));
				results.add(new OnboardResult(name, normalized, created, null));
			}
			catch (DomainException ex) {
				results.add(new OnboardResult(name, mobile, null, ex.getMessage()));
			}
		}
		long ok = results.stream().filter(OnboardResult::succeeded).count();
		audit.record(new AuditEntry("AGENTS_ONBOARDED", "USER", caller.userCode(), caller.orgId(), "USER_TYPE",
				Integer.toString(userType), Map.of("requested", Integer.toString(agents.size()), "created", Long.toString(ok))));
		return results;
	}

	private UserView create(Caller caller, int userType, AgentSignup agent, String name, String mobile, UUID parentId) {
		UserView user = userCommands.createUser(new NewUser(caller.orgId(), mobile, name, null, userType, false,
				UserStatus.ACTIVE, null, null, false, ShopView.empty(), parentId));
		if (agent.demo()) {
			LocalDate expires = agent.demoValidityDays() == null || agent.demoValidityDays() <= 0 ? null
					: LocalDate.now(clock).plusDays(agent.demoValidityDays());
			AppUser entity = users.findByIdAndOrgId(user.id(), caller.orgId()).orElseThrow();
			entity.makeDemo(expires);
		}
		return user;
	}

	/** Where the new agent sits: a distributor's own network, a named distributor, or the org root. */
	private UUID parentFor(Caller caller, AppUser self, int userType, AgentSignup agent) {
		if (caller.role() == Role.DISTRIBUTOR) {
			return self.getId();
		}
		String distributorMobile = agent.distributorMobile() == null ? "" : agent.distributorMobile().trim();
		if (userType != UserTypes.MERCHANT || distributorMobile.isEmpty()) {
			return null;
		}
		AppUser distributor = users
			.findByOrgIdAndMobileHash(caller.orgId(), index.hash(UserService.MOBILE_INDEX, Mobiles.normalize(distributorMobile)))
			.filter(u -> u.getUserType() == UserTypes.DISTRIBUTOR || u.getUserType() == UserTypes.SUPER_DISTRIBUTOR)
			.filter(u -> u.getStatus() == UserStatus.ACTIVE)
			.orElseThrow(() -> ValidationException.field("dist_mobile", "Distributor not found"));
		return distributor.getId();
	}

	private static void checkAllowed(Caller caller, AppUser self, int userType) {
		if (userType != UserTypes.MERCHANT && userType != UserTypes.DISTRIBUTOR) {
			throw ValidationException.field("applicant_type", "Only retailers and distributors can be onboarded here");
		}
		boolean admin = caller.role() == Role.ORG_ADMIN;
		boolean superDistributor = self.getUserType() == UserTypes.SUPER_DISTRIBUTOR;
		boolean distributor = caller.role() == Role.DISTRIBUTOR;
		boolean allowed = admin || superDistributor || (distributor && userType == UserTypes.MERCHANT);
		if (!allowed) {
			throw new ForbiddenException("NOT_ALLOWED", "You cannot onboard this type of agent.");
		}
	}
}
