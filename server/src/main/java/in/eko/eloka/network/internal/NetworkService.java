package in.eko.eloka.network.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.network.ActiveCount;
import in.eko.eloka.network.NetworkCommands;
import in.eko.eloka.network.NetworkFilter;
import in.eko.eloka.network.NetworkPage;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.PersonalInfo;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.network.UserTypes;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.shared.error.ForbiddenException;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Network queries and commands, always inside the caller's scope (see {@link Scope}).
 * Out-of-scope users are "not found", never "forbidden", so ids cannot be probed.
 */
@Service
@Transactional(readOnly = true)
class NetworkService implements NetworkQueries, NetworkCommands {

	/** What a caller may see: the whole org, or one subtree (hierarchy path prefix). */
	private record Scope(long orgId, String pathPrefix, UUID self) {

		boolean contains(AppUser user) {
			return user.getOrgId() == orgId && !user.getId().equals(self)
					&& (pathPrefix == null || (user.getHierarchyPath() != null && user.getHierarchyPath().startsWith(pathPrefix)));
		}
	}

	private final AppUserRepository users;
	private final ShopProfileRepository shops;
	private final UserMapper mapper;
	private final BlindIndex index;
	private final in.eko.eloka.shared.crypto.FieldCipher cipher;
	private final AuditLog audit;

	NetworkService(AppUserRepository users, ShopProfileRepository shops, UserMapper mapper, BlindIndex index,
			in.eko.eloka.shared.crypto.FieldCipher cipher, AuditLog audit) {
		this.users = users;
		this.shops = shops;
		this.mapper = mapper;
		this.index = index;
		this.cipher = cipher;
		this.audit = audit;
	}

	// ---------------------------------------------------------------- queries

	@Override
	public Map<Integer, ActiveCount> activeCountsByType(Caller caller) {
		if (!caller.role().equals(Role.ORG_ADMIN) && !caller.role().equals(Role.DISTRIBUTOR)) {
			return Map.of();
		}
		Scope scope = scopeOf(caller);
		List<Object[]> rows = scope.pathPrefix() == null ? users.countByTypeAndStatus(scope.orgId(), scope.self())
				: users.countByTypeAndStatusUnder(scope.orgId(), scope.pathPrefix(), scope.self());
		Map<Integer, long[]> counts = new TreeMap<>();
		for (Object[] row : rows) {
			if (row[1] == UserStatus.DELETED) {
				continue;
			}
			long[] pair = counts.computeIfAbsent((Integer) row[0], type -> new long[2]);
			long n = (Long) row[2];
			pair[1] += n;
			if (row[1] == UserStatus.ACTIVE) {
				pair[0] += n;
			}
		}
		Map<Integer, ActiveCount> result = new LinkedHashMap<>();
		counts.forEach((type, pair) -> result.put(type, new ActiveCount(pair[0], pair[1])));
		return result;
	}

	@Override
	public List<UUID> userIdsInScope(Caller caller) {
		return switch (caller.role()) {
			case ORG_ADMIN -> null;
			case DISTRIBUTOR -> {
				Scope scope = scopeOf(caller);
				yield users.idsUnder(scope.orgId(), scope.pathPrefix(), scope.self());
			}
			default -> List.of();
		};
	}

	@Override
	public Map<Integer, Long> joinedByType(Caller caller, LocalDate from, LocalDate to) {
		if (!caller.role().equals(Role.ORG_ADMIN) && !caller.role().equals(Role.DISTRIBUTOR)) {
			return Map.of();
		}
		Scope scope = scopeOf(caller);
		Map<Integer, Long> result = new TreeMap<>();
		for (Object[] row : users.joinedByType(scope.orgId(), scope.pathPrefix(), scope.self(), from, to)) {
			result.put((Integer) row[0], (Long) row[1]);
		}
		return result;
	}

	@Override
	public NetworkPage search(Caller caller, NetworkFilter filter, int page, int size) {
		Scope scope = scopeOf(caller);
		String prefix = scope.pathPrefix();
		if (filter.parentUserCode() != null) {
			AppUser parent = inScope(scope, findByCode(scope.orgId(), filter.parentUserCode()));
			prefix = parent.getHierarchyPath();
		}
		Specification<AppUser> spec = scopeSpec(scope.orgId(), prefix, scope.self()).and(filterSpec(scope.orgId(), filter));
		Page<AppUser> result = users.findAll(spec,
				PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt")));
		return new NetworkPage(result.getContent().stream().map(mapper::toView).toList(), result.getTotalElements());
	}

	@Override
	public List<UserView> members(Caller caller, Integer userType) {
		Scope scope = scopeOf(caller);
		Specification<AppUser> spec = scopeSpec(scope.orgId(), scope.pathPrefix(), scope.self());
		if (userType != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("userType"), userType));
		}
		return users.findAll(spec, Sort.by("name")).stream().map(mapper::toView).toList();
	}

	@Override
	public UserView findInScope(Caller caller, String mobileOrCode) {
		return mapper.toView(resolveInScope(scopeOf(caller), mobileOrCode));
	}

	@Override
	public Map<UUID, UserView> byIds(long orgId, Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return users.findByOrgIdAndIdIn(orgId, ids).stream().map(mapper::toView)
			.collect(Collectors.toMap(UserView::id, Function.identity()));
	}

	@Override
	public ShopView shopOf(Caller caller, UUID userId) {
		AppUser user = users.findByIdAndOrgId(userId, caller.orgId())
			.filter(u -> u.getId().equals(caller.userId()) || scopeOf(caller).contains(u))
			.orElseThrow(NetworkService::notFound);
		return shops.findByUserId(user.getId()).map(ShopProfile::toView).orElse(ShopView.empty());
	}

	// --------------------------------------------------------------- commands

	@Override
	@Transactional
	public void changeStatus(Caller caller, String userCode, UserStatus status, String note) {
		if (status != UserStatus.ACTIVE && status != UserStatus.INACTIVE) {
			throw ValidationException.field("agentAccountStatus", "Only active or inactive can be set here");
		}
		AppUser user = inScope(scopeOf(caller), findByCode(caller.orgId(), userCode));
		user.changeStatus(status);
		audit(caller, "USER_STATUS_CHANGED", user, Map.of("status", status.name(), "has_note", Boolean.toString(note != null)));
	}

	@Override
	@Transactional
	public void upgradeToIndependentRetailer(Caller caller, String mobile) {
		requireAdmin(caller);
		AppUser user = resolveInScope(scopeOf(caller), mobile);
		requireType(user, UserTypes.MERCHANT, "Only retailers can be upgraded");
		user.changeType(UserTypes.I_MERCHANT);
		moveSubtree(caller.orgId(), user, null);
		audit(caller, "USER_UPGRADED_TO_INDEPENDENT", user, Map.of());
	}

	@Override
	@Transactional
	public void promoteToDistributor(Caller caller, String mobile) {
		requireAdmin(caller);
		AppUser user = resolveInScope(scopeOf(caller), mobile);
		if (user.getUserType() != UserTypes.MERCHANT && user.getUserType() != UserTypes.I_MERCHANT) {
			throw new ConflictException("NOT_A_RETAILER", "Only retailers can be promoted");
		}
		user.changeType(UserTypes.DISTRIBUTOR);
		moveSubtree(caller.orgId(), user, null);
		audit(caller, "USER_PROMOTED_TO_DISTRIBUTOR", user, Map.of());
	}

	@Override
	@Transactional
	public void transfer(Caller caller, String toDistributorCode, List<String> userCodes) {
		requireAdmin(caller);
		Scope scope = scopeOf(caller);
		AppUser target = inScope(scope, findByCode(caller.orgId(), toDistributorCode));
		requireType(target, UserTypes.DISTRIBUTOR, "Agents can only be moved under a distributor");
		if (userCodes.isEmpty()) {
			throw ValidationException.field("selectedTransferredCSPsList", "Choose at least one agent");
		}
		for (String code : userCodes) {
			AppUser user = inScope(scope, findByCode(caller.orgId(), code));
			if (target.getHierarchyPath().startsWith(user.getHierarchyPath())) {
				throw new ConflictException("TRANSFER_CYCLE", "An agent cannot be moved under their own network");
			}
			moveSubtree(caller.orgId(), user, target);
			audit(caller, "USER_TRANSFERRED", user, Map.of("to", target.getUserCode()));
		}
	}

	@Override
	@Transactional
	public void updatePersonal(Caller caller, String userCode, String name, PersonalInfo personal) {
		AppUser user = selfOrInScope(caller, userCode);
		user.rename(name);
		user.updatePersonal(personal);
		audit(caller, "USER_PERSONAL_UPDATED", user, Map.of());
	}

	@Override
	@Transactional
	public void updateShop(Caller caller, String userCode, ShopView shop) {
		AppUser user = selfOrInScope(caller, userCode);
		shops.findByUserId(user.getId())
			.ifPresentOrElse(existing -> existing.update(shop), () -> shops.save(new ShopProfile(user.getId(), shop)));
		audit(caller, "USER_SHOP_UPDATED", user, Map.of());
	}

	@Override
	@Transactional
	public void setVerifiedBusinessEmail(Caller caller, String email) {
		AppUser user = self(caller);
		user.setVerifiedBusinessEmail(cipher.encrypt(email));
		audit(caller, "BUSINESS_EMAIL_VERIFIED", user, Map.of());
	}

	@Override
	@Transactional
	public void removeDemoUser(Caller caller, String userCode) {
		AppUser user = inScope(scopeOf(caller), findByCode(caller.orgId(), userCode));
		if (!user.isDemo()) {
			throw new ConflictException("NOT_A_DEMO_USER", "Only demo users can be removed");
		}
		user.changeStatus(UserStatus.DELETED);
		audit(caller, "DEMO_USER_REMOVED", user, Map.of());
	}

	// ---------------------------------------------------------------- helpers

	private Scope scopeOf(Caller caller) {
		return switch (caller.role()) {
			case ORG_ADMIN -> new Scope(caller.orgId(), null, caller.userId());
			case DISTRIBUTOR -> new Scope(caller.orgId(), self(caller).getHierarchyPath(), caller.userId());
			default -> throw new ForbiddenException("NOT_ALLOWED", "You are not allowed to do this.");
		};
	}

	private AppUser self(Caller caller) {
		return users.findByIdAndOrgId(caller.userId(), caller.orgId()).orElseThrow(NetworkService::notFound);
	}

	private AppUser selfOrInScope(Caller caller, String userCode) {
		if (userCode == null || userCode.equals(caller.userCode())) {
			return self(caller);
		}
		return inScope(scopeOf(caller), findByCode(caller.orgId(), userCode));
	}

	private AppUser findByCode(long orgId, String userCode) {
		return users.findByOrgIdAndUserCode(orgId, userCode).orElseThrow(NetworkService::notFound);
	}

	private AppUser resolveInScope(Scope scope, String mobileOrCode) {
		String value = mobileOrCode == null ? "" : mobileOrCode.trim();
		AppUser user;
		if (value.matches("(\\+?91)?[6-9]\\d{9}")) {
			user = users.findByOrgIdAndMobileHash(scope.orgId(), index.hash(UserService.MOBILE_INDEX, Mobiles.normalize(value)))
				.orElseThrow(NetworkService::notFound);
		}
		else {
			user = findByCode(scope.orgId(), value);
		}
		return inScope(scope, user);
	}

	private static AppUser inScope(Scope scope, AppUser user) {
		if (!scope.contains(user) || user.getStatus() == UserStatus.DELETED) {
			throw notFound();
		}
		return user;
	}

	/** Re-parents {@code user} (to {@code newParent}, or the org root) and rewrites the paths below it. */
	private void moveSubtree(long orgId, AppUser user, AppUser newParent) {
		String oldPrefix = user.getHierarchyPath();
		user.placeUnder(newParent);
		String newPrefix = user.getHierarchyPath();
		users.saveAndFlush(user);
		users.rebase(orgId, oldPrefix, oldPrefix.length(), newPrefix);
	}

	private static void requireAdmin(Caller caller) {
		if (caller.role() != Role.ORG_ADMIN) {
			throw new ForbiddenException("NOT_ALLOWED", "Only an admin can change roles.");
		}
	}

	private static void requireType(AppUser user, int type, String message) {
		if (user.getUserType() != type) {
			throw new ConflictException("WRONG_USER_TYPE", message);
		}
	}

	private void audit(Caller caller, String action, AppUser target, Map<String, String> details) {
		audit.record(new AuditEntry(action, "USER", caller.userCode(), caller.orgId(), "USER", target.getUserCode(), details));
	}

	private static NotFoundException notFound() {
		return new NotFoundException("USER_NOT_FOUND", "User not found");
	}

	private static Specification<AppUser> scopeSpec(long orgId, String pathPrefix, UUID self) {
		return (root, query, cb) -> {
			List<Predicate> p = new ArrayList<>();
			p.add(cb.equal(root.get("orgId"), orgId));
			p.add(cb.notEqual(root.get("status"), UserStatus.DELETED));
			p.add(cb.notEqual(root.get("id"), self));
			if (pathPrefix != null) {
				p.add(cb.like(root.get("hierarchyPath"), pathPrefix + "%"));
			}
			return cb.and(p.toArray(Predicate[]::new));
		};
	}

	private Specification<AppUser> filterSpec(long orgId, NetworkFilter f) {
		return (root, query, cb) -> {
			List<Predicate> p = new ArrayList<>();
			if (f.userType() != null) {
				p.add(cb.equal(root.get("userType"), f.userType()));
			}
			if (f.statusGroup() != null) {
				switch (f.statusGroup()) {
					case "Active" -> p.add(cb.equal(root.get("status"), UserStatus.ACTIVE));
					case "InActive" -> p.add(root.get("status").in(UserStatus.INACTIVE, UserStatus.BLOCKED));
					default -> p.add(cb.disjunction()); // InProgress: onboarding states arrive in P5
				}
			}
			if (f.joinedFrom() != null) {
				p.add(cb.greaterThanOrEqualTo(root.get("dateOfJoining"), f.joinedFrom()));
			}
			if (f.joinedTo() != null) {
				p.add(cb.lessThanOrEqualTo(root.get("dateOfJoining"), f.joinedTo()));
			}
			if (f.search() != null) {
				String value = f.search().trim();
				if (value.matches("(\\+?91)?[6-9]\\d{9}")) {
					p.add(cb.equal(root.get("mobileHash"), index.hash(UserService.MOBILE_INDEX, Mobiles.normalize(value))));
				}
				else {
					p.add(cb.equal(root.get("userCode"), value));
				}
			}
			return cb.and(p.toArray(Predicate[]::new));
		};
	}
}
