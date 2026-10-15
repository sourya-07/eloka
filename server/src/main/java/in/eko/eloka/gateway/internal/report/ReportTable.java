package in.eko.eloka.gateway.internal.report;

import java.util.List;

/** A table to export: a title, column headers and rows of already-formatted text. */
public record ReportTable(String title, List<String> headers, List<List<String>> rows) {
}
