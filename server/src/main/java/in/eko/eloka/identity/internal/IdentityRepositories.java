package in.eko.eloka.identity.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

	Optional<OtpChallenge> findFirstByOrgIdAndMobileHashAndPurposeOrderByCreatedAtDesc(long orgId, byte[] mobileHash,
			String purpose);

	long countByMobileHashAndCreatedAtAfter(byte[] mobileHash, Instant since);

	long countByClientIpHashAndCreatedAtAfter(byte[] clientIpHash, Instant since);
}

interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	Optional<AuthSession> findByAccessTokenHash(byte[] hash);

	Optional<AuthSession> findByRefreshTokenHash(byte[] hash);

	Optional<AuthSession> findByPreviousRefreshHash(byte[] hash);
}
