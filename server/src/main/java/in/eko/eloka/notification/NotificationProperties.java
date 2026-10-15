package in.eko.eloka.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param smsProvider {@code log} (no real SMS) or a provider name once one is added
 * @param smsRevealSecrets log the OTP itself (local/staging only, never production)
 */
@ConfigurationProperties("eloka.notification")
public record NotificationProperties(String smsProvider, boolean smsRevealSecrets) {

	public NotificationProperties {
		smsProvider = smsProvider == null ? "log" : smsProvider;
	}
}
