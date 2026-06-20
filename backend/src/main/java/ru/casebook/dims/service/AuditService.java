package ru.casebook.dims.service;

import org.springframework.stereotype.Service;
import ru.casebook.dims.domain.AuditLog;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.repo.AuditLogRepository;

import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    public void record(UserAccount actor, String action, String entityType, UUID entityId, String metadataJson) {
        auditLogs.save(new AuditLog(actor, action, entityType, entityId, metadataJson == null ? "{}" : metadataJson));
    }
}
