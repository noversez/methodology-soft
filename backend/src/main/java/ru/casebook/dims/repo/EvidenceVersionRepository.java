package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.casebook.dims.domain.EvidenceVersion;

import java.util.List;
import java.util.UUID;

public interface EvidenceVersionRepository extends JpaRepository<EvidenceVersion, UUID> {
    List<EvidenceVersion> findByEvidenceIdOrderByVersionNumberAsc(UUID evidenceId);

    @Query("select coalesce(max(item.versionNumber), 0) from EvidenceVersion item where item.evidence.id = :evidenceId")
    long maxVersionNumber(@Param("evidenceId") UUID evidenceId);
}
