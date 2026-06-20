package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.EvidenceVersion;

import java.util.List;
import java.util.UUID;

public interface EvidenceVersionRepository extends JpaRepository<EvidenceVersion, UUID> {
    List<EvidenceVersion> findByEvidenceIdOrderByVersionNumberAsc(UUID evidenceId);
}
