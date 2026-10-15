package in.eko.eloka.gateway.internal.handler;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.error.ValidationException;

/** EPS codes and value formats shared by several handlers. */
public final class EpsValues {

	/** EPS account status ids the client checks (NetworkMenu: 16 active, 18 inactive, 60 demo). */
	public static final int STATUS_ACTIVE = 16;
	public static final int STATUS_INACTIVE = 18;
	public static final int STATUS_DEMO = 60;

	private static final Map<Integer, String> TYPE_LABELS = Map.of(1, "Distributor", 2, "Retailer", 3,
			"Independent Retailer", 4, "Field Executive", 7, "Super Distributor", 23, "Enterprise Partner", 24, "Admin");

	private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
			DateTimeFormatter.ofPattern("ddMMyyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy"),
			DateTimeFormatter.ofPattern("dd/MM/yyyy"));

	private EpsValues() {
	}

	public static int statusId(UserView user) {
		if (user.demo() && user.status() == UserStatus.ACTIVE) {
			return STATUS_DEMO;
		}
		return user.status() == UserStatus.ACTIVE ? STATUS_ACTIVE : STATUS_INACTIVE;
	}

	public static String statusLabel(UserView user) {
		return switch (statusId(user)) {
			case STATUS_ACTIVE -> "Active";
			case STATUS_DEMO -> "Demo User";
			default -> "Inactive";
		};
	}

	public static String typeLabel(int userType) {
		return TYPE_LABELS.getOrDefault(userType, "User");
	}

	/** Accepts yyyy-MM-dd, ddMMyyyy (also as a number), dd-MM-yyyy and dd/MM/yyyy. */
	public static LocalDate date(String field, String value) {
		String v = value.trim();
		if (v.matches("\\d{7}")) {
			v = "0" + v; // ddMMyyyy sent as a number loses its leading zero
		}
		for (DateTimeFormatter format : DATE_FORMATS) {
			try {
				return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v, format);
			}
			catch (DateTimeParseException ignored) {
				// try the next format
			}
		}
		throw ValidationException.field(field, "Enter a valid date");
	}
}
