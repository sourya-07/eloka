package in.eko.eloka.gateway.internal.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Component;

@Component
class XlsxReportWriter implements ReportWriter {

	@Override
	public ReportFormat format() {
		return ReportFormat.XLSX;
	}

	@Override
	public byte[] write(ReportTable table) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			Workbook workbook = new Workbook(out, "ELOKA", "1.0");
			Worksheet sheet = workbook.newWorksheet(sheetName(table.title()));
			for (int c = 0; c < table.headers().size(); c++) {
				sheet.value(0, c, table.headers().get(c));
				sheet.style(0, c).bold().set();
			}
			for (int r = 0; r < table.rows().size(); r++) {
				List<String> row = table.rows().get(r);
				for (int c = 0; c < row.size(); c++) {
					sheet.value(r + 1, c, row.get(c));
				}
			}
			workbook.finish();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

	/** Excel sheet names: max 31 chars, no []:*?/\ characters. */
	private static String sheetName(String title) {
		String clean = title == null ? "Report" : title.replaceAll("[\\[\\]:*?/\\\\]", " ");
		return clean.length() > 31 ? clean.substring(0, 31) : clean;
	}
}
