package in.eko.eloka.history.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TxnRecordRepository extends JpaRepository<TxnRecord, UUID> {

	List<TxnRecord> findByOrgIdAndUserIdOrderByOccurredAtDesc(long orgId, UUID userId, Pageable page);

	/** Rows: txTypeId, status, count, sum(amount), sum(fee). */
	@Query("select t.txTypeId, t.status, count(t), coalesce(sum(t.amount), 0), coalesce(sum(t.fee), 0) from TxnRecord t "
			+ "where t.orgId = :orgId and (:allUsers = true or t.userId in :userIds) and t.occurredAt >= :from and t.occurredAt < :to "
			+ "and (:txType is null or t.txTypeId = :txType) group by t.txTypeId, t.status")
	List<Object[]> statsByTypeAndStatus(@Param("orgId") long orgId, @Param("allUsers") boolean allUsers,
			@Param("userIds") java.util.Collection<UUID> userIds, @Param("from") Instant from, @Param("to") Instant to,
			@Param("txType") Integer txType);

	/** Rows: userId, userCode, status, count, sum(amount), sum(fee). */
	@Query("select t.userId, t.userCode, t.status, count(t), coalesce(sum(t.amount), 0), coalesce(sum(t.fee), 0) from TxnRecord t "
			+ "where t.orgId = :orgId and (:allUsers = true or t.userId in :userIds) and t.occurredAt >= :from and t.occurredAt < :to "
			+ "and (:txType is null or t.txTypeId = :txType) group by t.userId, t.userCode, t.status")
	List<Object[]> statsByUserAndStatus(@Param("orgId") long orgId, @Param("allUsers") boolean allUsers,
			@Param("userIds") java.util.Collection<UUID> userIds, @Param("from") Instant from, @Param("to") Instant to,
			@Param("txType") Integer txType);

	@Query("select coalesce(sum(t.commission), 0) from TxnRecord t where t.orgId = :orgId and t.userId = :userId and t.status = in.eko.eloka.history.TxnStatus.SUCCESS and t.occurredAt >= :from and t.occurredAt < :to")
	BigDecimal sumCommission(@Param("orgId") long orgId, @Param("userId") UUID userId, @Param("from") Instant from,
			@Param("to") Instant to);
}
