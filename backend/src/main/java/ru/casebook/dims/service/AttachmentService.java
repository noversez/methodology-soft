package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.config.DimsProperties;
import ru.casebook.dims.domain.Attachment;
import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.repo.AttachmentRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.IOException;

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

    public AttachmentDownload download(UserAccount actor, UUID id) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT, Role.INSPECTOR, Role.AGENT, Role.LAB_ANALYST);
        Attachment attachment = attachments.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "File not found"));
        try {
            byte[] content = Files.readAllBytes(Path.of(attachment.getStoragePath()));
            if (!SecurityHash.sha256(content).equals(attachment.getSha256())) {
                throw new ApiException(HttpStatus.CONFLICT, "ATTACHMENT_INTEGRITY_VIOLATION", "File checksum mismatch");
            }
            return new AttachmentDownload(content, attachment.getMimeType(), attachment.getFileName());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_FILE_NOT_FOUND", "File is missing from storage");
        }
    }

    @Transactional
    public Attachment register(UserAccount actor, String ownerType, UUID ownerId, String fileName, String mimeType, long sizeBytes) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT, Role.INSPECTOR, Role.AGENT, Role.LAB_ANALYST);
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

    @Transactional
    public Attachment store(UserAccount actor, String ownerType, UUID ownerId, MultipartFile file, Instant capturedAt, Double latitude, Double longitude) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT, Role.INSPECTOR, Role.AGENT, Role.LAB_ANALYST);
        validateSize(file.getSize());
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "Нельзя загрузить пустой файл");
        String originalName = file.getOriginalFilename() == null ? "attachment.bin" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String safeName = originalName.replaceAll("[^A-Za-zА-Яа-я0-9._-]", "_");
        Path directory = Path.of(properties.getStoragePath(), ownerType.toLowerCase(), ownerId.toString()).toAbsolutePath().normalize();
        Path target = directory.resolve(UUID.randomUUID() + "-" + safeName).normalize();
        if (!target.startsWith(directory)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME", "Некорректное имя файла");
        try {
            byte[] content = file.getBytes();
            Files.createDirectories(directory);
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            String hash = SecurityHash.sha256(content);
            Attachment attachment = attachments.save(new Attachment(ownerType, ownerId, originalName,
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(), content.length,
                    target.toString(), hash, actor, capturedAt, latitude, longitude));
            auditService.record(actor, "ATTACHMENT_STORED", ownerType, ownerId, "{\"attachmentId\":\"" + attachment.getId() + "\",\"sha256\":\"" + hash + "\"}");
            return attachment;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", "Не удалось сохранить файл");
        }
    }

    private void validateSize(long sizeBytes) {
        long maxBytes = properties.getFileUploadMaxMb() * 1024L * 1024L;
        if (sizeBytes > maxBytes) throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Файл превышает лимит " + properties.getFileUploadMaxMb() + " MB");
    }
    public record AttachmentDownload(byte[] content, String mediaType, String fileName) {}
}
