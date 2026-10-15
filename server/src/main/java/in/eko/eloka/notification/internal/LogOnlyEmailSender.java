package in.eko.eloka.notification.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.notification.EmailMessage;
import in.eko.eloka.notification.EmailSender;
import in.eko.eloka.notification.NotificationProperties;
import in.eko.eloka.shared.config.SafetyCheck;
import in.eko.eloka.shared.crypto.Masking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Writes emails to the log instead of sending them. Not allowed in production. */
@Component
@ConditionalOnProperty(name = "eloka.notification.email-provider", havingValue = "log", matchIfMissing = true)
class LogOnlyEmailSender implements EmailSender, SafetyCheck {

	private static final Logger log = LoggerFactory.getLogger(LogOnlyEmailSender.class);

	private final NotificationProperties properties;

	LogOnlyEmailSender(NotificationProperties properties) {
		this.properties = properties;
	}

	@Override
	public void send(EmailMessage message) {
		Map<String, String> shown = new LinkedHashMap<>(message.params());
		String secret = message.secretParam() == null ? null : shown.remove(message.secretParam());
		log.info("[Email not sent: log-only provider] to={} template={} params={}", Masking.email(message.to()),
				message.template(), shown);
		if (secret != null && properties.smsRevealSecrets()) {
			log.info("[Email not sent] code for {} is {}", Masking.email(message.to()), secret);
		}
	}

	@Override
	public List<String> productionViolations() {
		return List.of("eloka.notification.email-provider is 'log': no real email provider configured");
	}
}
