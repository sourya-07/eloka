package in.eko.eloka.identity.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.identity.PinKey;
import in.eko.eloka.shared.error.ValidationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

interface PinTwinKeyRepository extends JpaRepository<PinTwinKey, Long> {
}

interface TransactionPinRepository extends JpaRepository<TransactionPin, UUID> {

	Optional<TransactionPin> findByUserId(UUID userId);
}

/** PinTwin keys and the transaction PIN. */
@Service
class PinService {

	private static final Duration KEY_TTL = Duration.ofMinutes(10);
	private static final String DIGITS = "0123456789";

	private final PinTwinKeyRepository keys;
	private final TransactionPinRepository pins;
	private final AuditLog audit;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();
	private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

	PinService(PinTwinKeyRepository keys, TransactionPinRepository pins, AuditLog audit, Clock clock) {
		this.keys = keys;
		this.pins = pins;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	PinKey issue(UUID sessionId) {
		List<Character> chars = new ArrayList<>();
		for (char c : DIGITS.toCharArray()) {
			chars.add(c);
		}
		Collections.shuffle(chars, random);
		StringBuilder key = new StringBuilder();
		chars.forEach(key::append);
		PinTwinKey saved = keys.save(new PinTwinKey(sessionId, key.toString(), Instant.now(clock).plus(KEY_TTL)));
		return new PinKey(saved.getId(), saved.getKeyChars());
	}

	@Transactional
	void setPin(long orgId, UUID userId, UUID sessionId, String encodedPin, String encodedConfirm) {
		Instant now = Instant.now(clock);
		Encoded first = Encoded.parse(encodedPin);
		Encoded second = Encoded.parse(encodedConfirm);
		if (first.keyId() != second.keyId()) {
			throw ValidationException.field("second_okekey", "Please enter the PIN again");
		}
		PinTwinKey key = keys.findById(first.keyId()).filter(k -> k.usableBy(sessionId, now))
			.orElseThrow(() -> ValidationException.field("first_okekey", "The PIN screen expired. Please enter the PIN again."));
		String pin = key.decode(first.code());
		String confirm = key.decode(second.code());
		if (pin == null || !pin.equals(confirm)) {
			throw ValidationException.field("second_okekey", "The two PINs do not match");
		}
		requireStrong(pin);
		key.use(now);
		String hash = bcrypt.encode(pin);
		pins.findByUserId(userId).ifPresentOrElse(existing -> existing.replace(hash),
				() -> pins.save(new TransactionPin(userId, hash)));
		audit.record(new AuditEntry("TRANSACTION_PIN_SET", "USER", userId.toString(), orgId, "USER", userId.toString(), Map.of()));
	}

	private static void requireStrong(String pin) {
		if (!pin.matches("\\d{4,6}")) {
			throw ValidationException.field("first_okekey", "The PIN must have 4 to 6 digits");
		}
		boolean allSame = pin.chars().distinct().count() == 1;
		boolean ascending = DIGITS.contains(pin);
		boolean descending = new StringBuilder(DIGITS).reverse().toString().contains(pin);
		if (allSame || ascending || descending) {
			throw ValidationException.field("first_okekey", "Choose a PIN that is harder to guess");
		}
	}

	/** {@code "<code>|<keyId>"}. */
	private record Encoded(String code, long keyId) {

		static Encoded parse(String value) {
			if (value == null || !value.matches("[^|]{4,6}\\|\\d+")) {
				throw ValidationException.field("first_okekey", "Please enter the PIN again");
			}
			int bar = value.indexOf('|');
			return new Encoded(value.substring(0, bar), Long.parseLong(value.substring(bar + 1)));
		}
	}
}
