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

    @PostMapping("/api/tasks/{id}/attachments")
    public AttachmentResponse taskAttachment(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody AttachmentRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return AttachmentResponse.from(attachmentService.register(actor, "Task", id, request.fileName(), request.mimeType(), request.sizeBytes()));
    }

    @GetMapping("/api/{ownerType}/{ownerId}/attachments")
    public List<AttachmentResponse> list(@PathVariable String ownerType, @PathVariable UUID ownerId) {
        return attachmentService.list(ownerType, ownerId).stream().map(AttachmentResponse::from).toList();
    }
}
