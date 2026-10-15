package in.eko.eloka.notification;

/**
 * Port for sending SMS. The provider is chosen by {@code eloka.notification.sms.provider}.
 * Only {@code log} exists today (writes to the log instead of sending); a real provider is
 * added as another implementation without touching callers.
 */
public interface SmsSender {

	void send(SmsMessage message);
}
