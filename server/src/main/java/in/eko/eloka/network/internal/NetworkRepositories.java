package in.eko.eloka.network.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByOrgIdAndMobileHash(long orgId, byte[] mobileHash);

	Optional<AppUser> findByIdAndOrgId(UUID id, long orgId);

	boolean existsByUserCode(String userCode);

	Optional<AppUser> findByOrgIdAndUserCode(long orgId, String userCode);

	@Query("select u.id from AppUser u where u.orgId = :orgId and u.hierarchyPath like concat(:path, '%') and u.id <> :exclude")
	List<UUID> idsUnder(@Param("orgId") long orgId, @Param("path") String path, @Param("exclude") UUID exclude);

	@Query("select u.userType, count(u) from AppUser u where u.orgId = :orgId and u.id <> :exclude and (:path is null or u.hierarchyPath like concat(:path, '%')) and u.dateOfJoining >= :from and u.dateOfJoining <= :to group by u.userType")
	List<Object[]> joinedByType(@Param("orgId") long orgId, @Param("path") String path, @Param("exclude") UUID exclude,
			@Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

	/** Counts per (user type, status) for a whole org. */
	@Query("select u.userType, u.status, count(u) from AppUser u where u.orgId = :orgId and u.id <> :exclude group by u.userType, u.status")
	List<Object[]> countByTypeAndStatus(@Param("orgId") long orgId, @Param("exclude") UUID exclude);

	/** Counts per (user type, status) below a node of the hierarchy. */
	@Query("select u.userType, u.status, count(u) from AppUser u where u.orgId = :orgId and u.hierarchyPath like concat(:path, '%') and u.id <> :exclude group by u.userType, u.status")
	List<Object[]> countByTypeAndStatusUnder(@Param("orgId") long orgId, @Param("path") String path, @Param("exclude") UUID exclude);
}

interface ShopProfileRepository extends JpaRepository<ShopProfile, UUID> {

	Optional<ShopProfile> findByUserId(UUID userId);
}

interface IdSequenceRepository extends JpaRepository<IdSequence, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from IdSequence s where s.name = :name")
	Optional<IdSequence> lock(@Param("name") String name);
}
