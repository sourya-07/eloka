package in.eko.eloka.gateway.internal.handler.network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import in.eko.eloka.gateway.internal.envelope.UploadedFile;
import in.eko.eloka.network.AgentSignup;
import in.eko.eloka.shared.error.ValidationException;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

/**
 * Reads the bulk-onboarding spreadsheet (Eko's sample layout): header row with
 * {@code Mobile}, {@code Name} and optionally {@code Distributor Mobile}. Accepts .xlsx and .csv.
 * Excel stores mobiles as numbers ({@code 6.01E9}), so numeric cells are converted back to digits.
 */
final class AgentSheetReader {

	private static final int MAX_BYTES = 5 * 1024 * 1024;

	private AgentSheetReader() {
	}

	static List<AgentSignup> read(UploadedFile file) {
		if (file.bytes().length == 0) {
			throw ValidationException.field("file_name", "The file is empty");
		}
		if (file.bytes().length > MAX_BYTES) {
			throw ValidationException.field("file_name", "The file is larger than 5 MB");
		}
		String name = Optional.ofNullable(file.filename()).orElse("").toLowerCase(Locale.ROOT);
		if (name.endsWith(".xls")) {
			throw ValidationException.field("file_name", "Old .xls files are not supported. Save the file as .xlsx and upload again.");
		}
		List<List<String>> rows = name.endsWith(".csv") ? csv(file.bytes()) : xlsx(file.bytes());
		if (rows.isEmpty()) {
			throw ValidationException.field("file_name", "The file has no rows");
		}
		List<String> header = rows.getFirst().stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
		int mobile = header.indexOf("mobile");
		int agentName = header.indexOf("name");
		int distributor = header.indexOf("distributor mobile");
		if (mobile < 0 || agentName < 0) {
			throw ValidationException.field("file_name", "The first row must have the columns Mobile and Name");
		}
		List<AgentSignup> agents = new ArrayList<>();
		for (List<String> row : rows.subList(1, rows.size())) {
			String m = cell(row, mobile);
			String n = cell(row, agentName);
			if (m.isEmpty() && n.isEmpty()) {
				continue; // blank line
			}
			agents.add(new AgentSignup(n, m, distributor < 0 ? null : cell(row, distributor), false, null));
		}
		return agents;
	}

	private static List<List<String>> xlsx(byte[] bytes) {
		try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(bytes))) {
			Sheet sheet = workbook.getFirstSheet();
			List<List<String>> rows = new ArrayList<>();
			try (var stream = sheet.openStream()) {
				stream.forEach(row -> rows.add(values(row)));
			}
			return rows;
		}
		catch (IOException | RuntimeException ex) {
			throw ValidationException.field("file_name", "Could not read the file. Upload an .xlsx file.");
		}
	}

	private static List<String> values(Row row) {
		List<String> values = new ArrayList<>();
		for (int i = 0; i < row.getCellCount(); i++) {
			Cell cell = row.getCell(i);
			if (cell == null) {
				values.add("");
			}
			else if (cell.getRawValue() != null && cell.getType() == org.dhatim.fastexcel.reader.CellType.NUMBER) {
				values.add(new BigDecimal(cell.getRawValue()).toPlainString().replaceAll("\\.0+$", ""));
			}
			else {
				values.add(Optional.ofNullable(cell.getText()).orElse(""));
			}
		}
		return values;
	}

	private static List<List<String>> csv(byte[] bytes) {
		List<List<String>> rows = new ArrayList<>();
		for (String line : new String(bytes, StandardCharsets.UTF_8).replace("﻿", "").split("\\r?\\n")) {
			List<String> cells = new ArrayList<>();
			for (String cell : line.split(",", -1)) {
				cells.add(cell.replace("\"", "").trim());
			}
			rows.add(cells);
		}
		return rows;
	}

	private static String cell(List<String> row, int index) {
		return index < row.size() ? row.get(index).trim() : "";
	}
}
