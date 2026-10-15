package in.eko.eloka.gateway.internal.report;

import java.util.Locale;

/** Export formats the client can ask for ({@code reporttype}). */
public enum ReportFormat {
	XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
	PDF("pdf", "application/pdf"),
	CSV("csv", "text/csv");

	private final String extension;
	private final String contentType;

	ReportFormat(String extension, String contentType) {
		this.extension = extension;
		this.contentType = contentType;
	}

	public String extension() {
		return extension;
	}

	public String contentType() {
		return contentType;
	}

	/** Unknown or missing → {@code fallback}. */
	public static ReportFormat of(String value, ReportFormat fallback) {
		if (value == null) {
			return fallback;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "xlsx", "excel", "xls" -> XLSX;
			case "pdf" -> PDF;
			case "csv" -> CSV;
			default -> fallback;
		};
	}
}
