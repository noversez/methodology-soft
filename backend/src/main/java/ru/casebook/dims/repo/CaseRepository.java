package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.CaseFile;

import java.util.UUID;

public interface CaseRepository extends JpaRepository<CaseFile, UUID> {
    java.util.Optional<CaseFile> findByRegistrationNumber(String registrationNumber);
    long countByRegistrationNumberStartingWith(String prefix);
}
