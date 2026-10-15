package in.eko.eloka.gateway.internal.report;

/** Strategy: renders a {@link ReportTable} in one format. */
interface ReportWriter {

	ReportFormat format();

	byte[] write(ReportTable table);
}
