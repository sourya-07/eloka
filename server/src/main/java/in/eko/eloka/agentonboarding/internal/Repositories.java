package in.eko.eloka.agentonboarding.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AgentOnboardingRepository extends JpaRepository<AgentOnboarding, UUID> {

	Optional<AgentOnboarding> findByOrgIdAndUserId(long orgId, UUID userId);

	List<AgentOnboarding> findByOrgIdAndUserIdIn(long orgId, Collection<UUID> userIds);

	List<AgentOnboarding> findByOrgIdAndCompletedAtIsNull(long orgId);
}

interface AgentKycRepository extends JpaRepository<AgentKyc, UUID> {

	Optional<AgentKyc> findByOrgIdAndUserId(long orgId, UUID userId);

	boolean existsByOrgIdAndPanHashAndUserIdNot(long orgId, byte[] panHash, UUID userId);
}
