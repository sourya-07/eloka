package in.eko.eloka.notification.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.notification.NotificationProperties;
import in.eko.eloka.notification.SmsMessage;
import in.eko.eloka.notification.SmsSender;
import in.eko.eloka.shared.config.SafetyCheck;
import in.eko.eloka.shared.crypto.Masking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes messages to the log instead of sending them. Production refuses to start with
 * it, because users could not receive their OTP.
 */
@Component
@ConditionalOnProperty(name = "eloka.notification.sms-provider", havingValue = "log", matchIfMissing = true)
class LogOnlySmsSender implements SmsSender, SafetyCheck {

	private static final Logger log = LoggerFactory.getLogger(LogOnlySmsSender.class);

	private final NotificationProperties properties;

	LogOnlySmsSender(NotificationProperties properties) {
		this.properties = properties;
	}

	@Override
	public void send(SmsMessage message) {
		Map<String, String> shown = new LinkedHashMap<>(message.params());
		String secret = message.secretParam() == null ? null : shown.remove(message.secretParam());
		log.info("[SMS not sent: log-only provider] to={} template={} params={}", Masking.mobile(message.mobile()),
				message.template(), shown);
		if (secret != null && properties.smsRevealSecrets()) {
			// Separate line on purpose: the log masker redacts "otp=..." pairs.
			log.info("[SMS not sent] code for {} is {}", Masking.mobile(message.mobile()), secret);
		}
	}

	@Override
	public List<String> productionViolations() {
		List<String> problems = new ArrayList<>();
		problems.add("eloka.notification.sms-provider is 'log': no real SMS provider configured");
		if (properties.smsRevealSecrets()) {
			problems.add("eloka.notification.sms-reveal-secrets=true writes OTPs to the log");
		}
		return problems;
	}
}
