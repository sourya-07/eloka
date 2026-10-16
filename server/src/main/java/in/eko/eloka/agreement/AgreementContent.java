package in.eko.eloka.agreement;

/**
 * Everything printed in the agreement. Brand name and sub-domain are not printed (OQ-P10), so
 * changing them does not need a new signature.
 */
public record AgreementContent(String legalName, String entityType, String panMasked, String signatoryName,
		String address, String emailMasked) {
}
