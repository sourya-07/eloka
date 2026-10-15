package in.eko.eloka.gateway.internal.handler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import in.eko.eloka.shared.error.ValidationException;

/** Date/time conventions of the EPS wire format: Indian time, {@code yyyy-MM-dd HH:mm:ss}. */
public final class EpsDates {

	public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private EpsDates() {
	}

	public static String format(Instant instant) {
		return instant == null ? null : DATE_TIME.format(instant.atZone(IST));
	}

	/** Parses {@code yyyy-MM-dd HH:mm:ss} or {@code yyyy-MM-dd} as Indian time. */
	public static Instant parse(String field, String value) {
		try {
			if (value.length() <= 10) {
				return LocalDate.parse(value).atStartOfDay(IST).toInstant();
			}
			return LocalDateTime.parse(value, DATE_TIME).atZone(IST).toInstant();
		}
		catch (DateTimeParseException ex) {
			throw ValidationException.field(field, "Use the format yyyy-MM-dd HH:mm:ss");
		}
	}

	public static LocalDate localDate(Instant instant) {
		return instant.atZone(IST).toLocalDate();
	}
}
