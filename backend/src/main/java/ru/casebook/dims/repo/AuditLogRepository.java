package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.AuditLog;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
