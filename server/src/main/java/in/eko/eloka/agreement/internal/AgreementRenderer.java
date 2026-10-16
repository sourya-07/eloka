package in.eko.eloka.agreement.internal;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import in.eko.eloka.agreement.AgreementContent;
import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * Renders the Local Admin agreement. The legal text is a placeholder until legal owns a
 * template (OQ-B10); the template version is recorded with every agreement.
 */
@Component
class AgreementRenderer {

	static final String TEMPLATE_VERSION = "LA-2026-10";

	/** The exact text that gets signed; its SHA-256 identifies the agreement version. */
	String text(AgreementContent content) {
		return String.join("\n", List.of(
				"ELOKA LOCAL ADMIN AGREEMENT (template " + TEMPLATE_VERSION + ")",
				"Partner: " + nullSafe(content.legalName()),
				"Legal form: " + nullSafe(content.entityType()),
				"PAN: " + nullSafe(content.panMasked()),
				"Authorised signatory: " + nullSafe(content.signatoryName()),
				"Registered address: " + nullSafe(content.address()),
				"Email: " + nullSafe(content.emailMasked()),
				"",
				"1. The Partner will operate a white-label ELOKA portal and appoint agents under it.",
				"2. The Partner keeps agent onboarding, KYC and conduct within the rules Eko publishes.",
				"3. Eko may suspend the portal for compliance, security or non-payment reasons.",
				"4. Personal data is processed under the consent recorded during onboarding.",
				"",
				"[Placeholder text: replace with the legal template before production (OQ-B10).]"));
	}

	byte[] pdf(String text, LocalDate date) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 48, 48, 48, 48);
		PdfWriter.getInstance(document, out);
		document.open();
		Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
		Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
		String[] lines = text.split("\n");
		document.add(new Paragraph(lines[0], title));
		for (int i = 1; i < lines.length; i++) {
			document.add(new Paragraph(lines[i].isEmpty() ? " " : lines[i], body));
		}
		document.add(new Paragraph(" ", body));
		document.add(new Paragraph("Generated " + date, body));
		document.close();
		return out.toByteArray();
	}

	private static String nullSafe(String value) {
		return value == null ? "-" : value;
	}
}
