package in.eko.eloka.tenant.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrgRepository extends JpaRepository<Org, Long> {
}

interface OrgDomainRepository extends JpaRepository<OrgDomain, UUID> {

	Optional<OrgDomain> findByHostnameAndActiveTrue(String hostname);
}

interface OrgMetadataRepository extends JpaRepository<OrgMetadataEntry, UUID> {

	List<OrgMetadataEntry> findByOrgId(long orgId);

	Optional<OrgMetadataEntry> findByOrgIdAndKey(long orgId, String key);
}
