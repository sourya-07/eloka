package in.eko.eloka.gateway.internal.report;

import java.io.ByteArrayOutputStream;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

@Component
class PdfReportWriter implements ReportWriter {

	@Override
	public ReportFormat format() {
		return ReportFormat.PDF;
	}

	@Override
	public byte[] write(ReportTable table) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
		PdfWriter.getInstance(document, out);
		document.open();
		Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
		Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
		Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 7);
		Paragraph title = new Paragraph(table.title(), titleFont);
		title.setSpacingAfter(8);
		document.add(title);

		PdfPTable pdf = new PdfPTable(Math.max(1, table.headers().size()));
		pdf.setWidthPercentage(100);
		pdf.setHeaderRows(1);
		for (String header : table.headers()) {
			PdfPCell cell = new PdfPCell(new Phrase(header, headFont));
			cell.setHorizontalAlignment(Element.ALIGN_LEFT);
			pdf.addCell(cell);
		}
		for (var row : table.rows()) {
			for (String value : row) {
				pdf.addCell(new Phrase(value == null ? "" : value, cellFont));
			}
		}
		document.add(pdf);
		document.close();
		return out.toByteArray();
	}
}
