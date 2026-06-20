package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.ReportFile;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<ReportFile, UUID> {
    List<ReportFile> findByCaseFileId(UUID caseId);
    long countByRegistrationNumberStartingWith(String prefix);
}
