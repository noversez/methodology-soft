package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.config.DimsProperties;
import ru.casebook.dims.domain.Attachment;
import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.repo.AttachmentRepository;

import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {
    private final AttachmentRepository attachments;
    private final DimsProperties properties;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public AttachmentService(AttachmentRepository attachments, DimsProperties properties, CurrentUserService currentUserService, AuditService auditService) {
        this.attachments = attachments;
        this.properties = properties;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    public List<Attachment> list(String ownerType, UUID ownerId) {
        return attachments.findByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    @Transactional
    public Attachment register(UserAccount actor, String ownerType, UUID ownerId, String fileName, String mimeType, long sizeBytes) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT, Role.AGENT, Role.LAB_ANALYST);
        long maxBytes = properties.getFileUploadMaxMb() * 1024L * 1024L;
        if (sizeBytes > maxBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Файл превышает лимит " + properties.getFileUploadMaxMb() + " MB");
        }
        String hash = SecurityHash.sha256(ownerType + ownerId + fileName + sizeBytes);
        String path = properties.getStoragePath() + "/" + ownerType.toLowerCase() + "/" + ownerId + "/" + fileName;
        Attachment attachment = attachments.save(new Attachment(ownerType, ownerId, fileName, mimeType, sizeBytes, path, hash, actor));
        auditService.record(actor, "ATTACHMENT_REGISTERED", ownerType, ownerId, "{\"attachmentId\":\"" + attachment.getId() + "\"}");
        return attachment;
    }
}
