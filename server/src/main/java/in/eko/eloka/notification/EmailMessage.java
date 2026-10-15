package in.eko.eloka.notification;

import java.util.Map;

/**
 * One email.
 *
 * @param secretParam name of a param that is a secret (OTP) and must never be logged, or null
 */
public record EmailMessage(String to, String template, String subject, Map<String, String> params, String secretParam) {

	public EmailMessage {
		params = params == null ? Map.of() : Map.copyOf(params);
	}
}
