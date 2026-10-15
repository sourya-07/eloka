package in.eko.eloka.network.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserCreated;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserProfile;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Masking;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.shared.error.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looking up and creating users. Network-scoped questions live in {@link NetworkService}. */
@Service
@Transactional(readOnly = true)
class UserService implements UserDirectory, UserCommands {

	static final String MOBILE_INDEX = "mobile";
	private static final String USER_CODE_SEQUENCE = "user_code";

	private final AppUserRepository users;
	private final ShopProfileRepository shops;
	private final IdSequenceRepository sequences;
	private final UserMapper mapper;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final AuditLog audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	UserService(AppUserRepository users, ShopProfileRepository shops, IdSequenceRepository sequences, UserMapper mapper,
			FieldCipher cipher, BlindIndex index, AuditLog audit, ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.shops = shops;
		this.sequences = sequences;
		this.mapper = mapper;
		this.cipher = cipher;
		this.index = index;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Override
	public Optional<UserView> findByMobile(long orgId, String mobile) {
		String normalized = Mobiles.normalize(mobile);
		return users.findByOrgIdAndMobileHashAndStatusNot(orgId, index.hash(MOBILE_INDEX, normalized), UserStatus.DELETED)
			.map(mapper::toView);
	}

	@Override
	public Optional<UserView> findByUserCode(long orgId, String userCode) {
		return users.findByOrgIdAndUserCode(orgId, userCode).filter(u -> u.getStatus() != UserStatus.DELETED).map(mapper::toView);
	}

	@Override
	public UserView getById(long orgId, UUID userId) {
		return mapper.toView(load(orgId, userId));
	}

	@Override
	public UserProfile profile(long orgId, UUID userId) {
		AppUser user = load(orgId, userId);
		ShopView shop = shops.findByUserId(user.getId()).map(ShopProfile::toView).orElse(ShopView.empty());
		return new UserProfile(mapper.toView(user), shop);
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
		return mapper.toView(user);
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
}
