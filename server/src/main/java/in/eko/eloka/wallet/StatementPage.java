package in.eko.eloka.wallet;

import java.util.List;

/** A page of statement lines plus the total number of lines that match. */
public record StatementPage(List<LedgerLine> lines, long total) {
}
