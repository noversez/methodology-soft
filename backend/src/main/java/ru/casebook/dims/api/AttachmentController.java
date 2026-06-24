package ru.casebook.dims.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.AttachmentDtos.AttachmentRequest;
import ru.casebook.dims.api.dto.AttachmentDtos.AttachmentResponse;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.service.AttachmentService;
import ru.casebook.dims.service.CurrentUserService;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

@RestController
public class AttachmentController {
    private final AttachmentService attachmentService;
    private final CurrentUserService currentUserService;

    public AttachmentController(AttachmentService attachmentService, CurrentUserService currentUserService) {
        this.attachmentService = attachmentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/evidence/{id}/attachments")
    public AttachmentResponse evidenceAttachment(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody AttachmentRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return AttachmentResponse.from(attachmentService.register(actor, "Evidence", id, request.fileName(), request.mimeType(), request.sizeBytes()));
    }

    @PostMapping(value = "/api/evidence/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse uploadEvidenceAttachment(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Instant capturedAt,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        return AttachmentResponse.from(attachmentService.store(currentUserService.requireUser(userId), "Evidence", id, file, capturedAt, latitude, longitude));
    }

    @PostMapping("/api/tasks/{id}/attachments")
    public AttachmentResponse taskAttachment(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody AttachmentRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return AttachmentResponse.from(attachmentService.register(actor, "Task", id, request.fileName(), request.mimeType(), request.sizeBytes()));
    }

    @PostMapping(value = "/api/tasks/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse uploadTaskAttachment(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Instant capturedAt,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        return AttachmentResponse.from(attachmentService.store(currentUserService.requireUser(userId), "Task", id, file, capturedAt, latitude, longitude));
    }

    @GetMapping("/api/{ownerType}/{ownerId}/attachments")
    public List<AttachmentResponse> list(@PathVariable String ownerType, @PathVariable UUID ownerId) {
        return attachmentService.list(ownerType, ownerId).stream().map(AttachmentResponse::from).toList();
    }

    @GetMapping("/api/attachments/{id}/download")
    public ResponseEntity<byte[]> download(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id) {
        var file = attachmentService.download(currentUserService.requireUser(userId), id);
        String encodedName = UriUtils.encode(file.fileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .body(file.content());
    }
}
