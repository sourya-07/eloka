package in.eko.eloka.network.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.network.ActiveCount;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCreated;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserProfile;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Masking;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserService implements UserDirectory, UserCommands, NetworkQueries {

	static final String MOBILE_INDEX = "mobile";
	private static final String USER_CODE_SEQUENCE = "user_code";

	private final AppUserRepository users;
	private final ShopProfileRepository shops;
	private final IdSequenceRepository sequences;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final AuditLog audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	UserService(AppUserRepository users, ShopProfileRepository shops, IdSequenceRepository sequences, FieldCipher cipher,
			BlindIndex index, AuditLog audit, ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.shops = shops;
		this.sequences = sequences;
		this.cipher = cipher;
		this.index = index;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Override
	public Optional<UserView> findByMobile(long orgId, String mobile) {
		String normalized = Mobiles.normalize(mobile);
		return users.findByOrgIdAndMobileHash(orgId, index.hash(MOBILE_INDEX, normalized)).map(this::toView);
	}

	@Override
	public Optional<UserView> findByUserCode(long orgId, String userCode) {
		return users.findByOrgIdAndUserCode(orgId, userCode).map(this::toView);
	}

	@Override
	public Map<Integer, ActiveCount> activeCountsByType(Caller caller) {
		List<Object[]> rows;
		if (caller.role() == Role.ORG_ADMIN) {
			rows = users.countByTypeAndStatus(caller.orgId(), caller.userId());
		}
		else if (caller.role() == Role.DISTRIBUTOR) {
			rows = users.countByTypeAndStatusUnder(caller.orgId(), load(caller.orgId(), caller.userId()).getHierarchyPath(),
					caller.userId());
		}
		else {
			return Map.of();
		}
		Map<Integer, long[]> counts = new java.util.TreeMap<>();
		for (Object[] row : rows) {
			long[] pair = counts.computeIfAbsent((Integer) row[0], type -> new long[2]);
			long n = (Long) row[2];
			pair[1] += n;
			if (row[1] == in.eko.eloka.network.UserStatus.ACTIVE) {
				pair[0] += n;
			}
		}
		Map<Integer, ActiveCount> result = new java.util.LinkedHashMap<>();
		counts.forEach((type, pair) -> result.put(type, new ActiveCount(pair[0], pair[1])));
		return result;
	}

	@Override
	public List<UUID> userIdsInScope(Caller caller) {
		return switch (caller.role()) {
			case ORG_ADMIN -> null;
			case DISTRIBUTOR -> users.idsUnder(caller.orgId(), load(caller.orgId(), caller.userId()).getHierarchyPath(),
					caller.userId());
			default -> List.of();
		};
	}

	@Override
	public Map<Integer, Long> joinedByType(Caller caller, LocalDate from, LocalDate to) {
		String path = switch (caller.role()) {
			case ORG_ADMIN -> null;
			case DISTRIBUTOR -> load(caller.orgId(), caller.userId()).getHierarchyPath();
			default -> "/no-scope/";
		};
		Map<Integer, Long> result = new java.util.TreeMap<>();
		for (Object[] row : users.joinedByType(caller.orgId(), path, caller.userId(), from, to)) {
			result.put((Integer) row[0], (Long) row[1]);
		}
		return result;
	}

	@Override
	public UserView getById(long orgId, UUID userId) {
		return toView(load(orgId, userId));
	}

	@Override
	public UserProfile profile(long orgId, UUID userId) {
		AppUser user = load(orgId, userId);
		ShopView shop = shops.findByUserId(user.getId()).map(ShopProfile::toView).orElse(ShopView.empty());
		return new UserProfile(toView(user), shop);
	}

	@Override
	@Transactional
	public UserView createUser(NewUser request) {
		String mobile = Mobiles.normalize(request.mobile());
		byte[] mobileHash = index.hash(MOBILE_INDEX, mobile);
		if (users.findByOrgIdAndMobileHash(request.orgId(), mobileHash).isPresent()) {
			throw new ConflictException("MOBILE_ALREADY_REGISTERED", "This mobile number already has an account");
		}
		String userCode = request.userCode() != null ? request.userCode() : nextUserCode();
		if (users.existsByUserCode(userCode)) {
			throw new ConflictException("USER_CODE_TAKEN", "User code already in use");
		}
		LocalDate joined = request.dateOfJoining() != null ? request.dateOfJoining() : LocalDate.now(clock);
		AppUser user = users.save(new AppUser(request.orgId(), userCode, request.userType(), request.orgAdmin(),
				request.status(), request.name(), cipher.encrypt(mobile), mobileHash, Masking.mobile(mobile),
				cipher.encrypt(request.email()), joined, request.demo()));
		AppUser parent = request.parentUserId() == null ? null : load(request.orgId(), request.parentUserId());
		user.placeUnder(parent);
		if (request.shop() != null) {
			shops.save(new ShopProfile(user.getId(), request.shop()));
		}
		audit.record(new AuditEntry("USER_CREATED", "SYSTEM", null, request.orgId(), "USER", userCode,
				Map.of("user_type", Integer.toString(request.userType()))));
		events.publishEvent(new UserCreated(user.getId(), request.orgId(), userCode, request.userType()));
		return toView(user);
	}

	private AppUser load(long orgId, UUID userId) {
		return users.findByIdAndOrgId(userId, orgId)
			.orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
	}

	private String nextUserCode() {
		IdSequence sequence = sequences.lock(USER_CODE_SEQUENCE)
			.orElseThrow(() -> new IllegalStateException("id_sequence 'user_code' is missing"));
		return Long.toString(sequence.take());
	}

	private UserView toView(AppUser user) {
		return new UserView(user.getId(), user.getOrgId(), user.getUserCode(), user.getUserType(), user.isOrgAdmin(),
				user.getStatus(), user.getName(), cipher.decrypt(user.getMobileEnc()), cipher.decrypt(user.getEmailEnc()),
				user.getDateOfJoining(), user.isDemo(), user.getParentUserId());
	}
}
