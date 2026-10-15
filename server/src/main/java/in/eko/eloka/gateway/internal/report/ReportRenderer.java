package in.eko.eloka.gateway.internal.report;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Picks the writer for a format. */
@Component
public class ReportRenderer {

	/** A rendered file. */
	public record Rendered(byte[] bytes, String fileName, String contentType) {
	}

	private final Map<ReportFormat, ReportWriter> writers = new EnumMap<>(ReportFormat.class);

	ReportRenderer(List<ReportWriter> all) {
		all.forEach(writer -> writers.put(writer.format(), writer));
	}

	public Rendered render(ReportTable table, ReportFormat format, String baseName) {
		return new Rendered(writers.get(format).write(table), baseName + "." + format.extension(), format.contentType());
	}
}
