package in.eko.eloka.gateway.internal.report;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class CsvReportWriter implements ReportWriter {

	@Override
	public ReportFormat format() {
		return ReportFormat.CSV;
	}

	@Override
	public byte[] write(ReportTable table) {
		StringBuilder csv = new StringBuilder("﻿"); // BOM so Excel opens UTF-8 correctly
		csv.append(line(table.headers()));
		table.rows().forEach(row -> csv.append(line(row)));
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String line(List<String> values) {
		return values.stream().map(CsvReportWriter::escape).collect(Collectors.joining(",")) + "\r\n";
	}

	/** Quotes when needed and neutralises spreadsheet formulas (CSV injection). */
	private static String escape(String value) {
		String v = value == null ? "" : value;
		if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
			v = "'" + v;
		}
		if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
			v = "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}
}
