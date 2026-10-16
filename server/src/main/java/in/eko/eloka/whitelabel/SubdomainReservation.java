package in.eko.eloka.whitelabel;

/** A held sub-domain: {@code hostname} = {@code label.<root domain>}. */
public record SubdomainReservation(String label, String hostname) {
}
