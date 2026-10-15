package in.eko.eloka.notification;

/** Port for sending email ({@code eloka.notification.email-provider}). Only {@code log} exists today. */
public interface EmailSender {

	void send(EmailMessage message);
}
