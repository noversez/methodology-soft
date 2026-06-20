package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.Evidence;
import ru.casebook.dims.domain.EvidenceStatus;

import java.util.List;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {
    List<Evidence> findByCaseFileId(UUID caseId);
    long countByRegistrationNumberStartingWith(String prefix);
    long countByIdAndStatus(UUID id, EvidenceStatus status);
}
