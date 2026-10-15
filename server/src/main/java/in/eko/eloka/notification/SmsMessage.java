package in.eko.eloka.notification;

import java.util.Map;

/**
 * One text message.
 *
 * @param mobile 10-digit recipient
 * @param template DLT template code, e.g. {@code LOGIN_OTP}
 * @param params template values
 * @param secretParam name of a param that is a secret (OTP) and must never be logged, or null
 */
public record SmsMessage(String mobile, String template, Map<String, String> params, String secretParam) {

	public SmsMessage {
		params = params == null ? Map.of() : Map.copyOf(params);
	}
}
