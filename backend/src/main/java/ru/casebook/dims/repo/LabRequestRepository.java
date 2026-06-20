package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.LabRequest;
import ru.casebook.dims.domain.LabRequestStatus;

import java.util.List;
import java.util.UUID;

public interface LabRequestRepository extends JpaRepository<LabRequest, UUID> {
    List<LabRequest> findByCaseFileId(UUID caseId);
    List<LabRequest> findByEvidenceId(UUID evidenceId);
    List<LabRequest> findByLabAssigneeIdOrderByDesiredDueDateAsc(UUID labAssigneeId);
    boolean existsByEvidenceIdAndStatusNot(UUID evidenceId, LabRequestStatus status);
}
