package in.eko.eloka.onboarding;

import java.util.Arrays;
import java.util.Optional;

/** Legal form of the applicant's business; {@code code} is the value the API uses (1–4). */
public enum EntityType {
	PRIVATE_LIMITED(1),
	LLP(2),
	PARTNERSHIP(3),
	SOLE_PROPRIETORSHIP(4);

	private final int code;

	EntityType(int code) {
		this.code = code;
	}

	public int code() {
		return code;
	}

	public static Optional<EntityType> fromCode(int code) {
		return Arrays.stream(values()).filter(type -> type.code == code).findFirst();
	}
}
