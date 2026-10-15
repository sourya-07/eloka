package in.eko.eloka.shared.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Paging by row offset (EPS sends {@code start_index} + {@code limit}, not page numbers). */
public final class OffsetPage implements Pageable {

	private final long offset;
	private final int limit;
	private final Sort sort;

	public OffsetPage(long offset, int limit, Sort sort) {
		if (offset < 0 || limit < 1) {
			throw new IllegalArgumentException("offset >= 0 and limit >= 1 required");
		}
		this.offset = offset;
		this.limit = limit;
		this.sort = sort;
	}

	@Override public int getPageNumber() { return (int) (offset / limit); }
	@Override public int getPageSize() { return limit; }
	@Override public long getOffset() { return offset; }
	@Override public Sort getSort() { return sort; }
	@Override public Pageable next() { return new OffsetPage(offset + limit, limit, sort); }
	@Override public Pageable previousOrFirst() { return hasPrevious() ? new OffsetPage(Math.max(0, offset - limit), limit, sort) : first(); }
	@Override public Pageable first() { return new OffsetPage(0, limit, sort); }
	@Override public Pageable withPage(int pageNumber) { return new OffsetPage((long) pageNumber * limit, limit, sort); }
	@Override public boolean hasPrevious() { return offset > 0; }
}
