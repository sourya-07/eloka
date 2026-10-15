package in.eko.eloka.support;

import java.util.List;

/**
 * A kind of issue a user can raise (EPS 10022 {@code issuetype_list} item).
 *
 * @param comment -1 hidden, 0 optional, 1 mandatory
 * @param inputs extra fields to ask for
 * @param files files to ask for
 */
public record IssueType(String label, String description, int categoryId, String category, int subCategoryId,
		String subCategory, String tatDays, String priority, int comment, List<Input> inputs, List<FileRequest> files) {

	/** @param type 11 numeric, 9 text */
	public record Input(String label, int type, boolean required, int maxLength) {
	}

	public record FileRequest(String label, boolean required) {
	}
}
