package ru.casebook.dims.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import ru.casebook.dims.domain.Attachment;

import java.time.Instant;
import java.util.UUID;

public final class AttachmentDtos {
    private AttachmentDtos() {
    }

    public record AttachmentRequest(@NotBlank String fileName, @NotBlank String mimeType, @Min(1) long sizeBytes) {
    }

    public record AttachmentResponse(UUID id, String ownerType, UUID ownerId, String fileName, String mimeType, long sizeBytes, String storagePath, String sha256, UUID uploadedBy, Instant createdAt) {
        public static AttachmentResponse from(Attachment item) {
            return new AttachmentResponse(
                    item.getId(),
                    item.getOwnerType(),
                    item.getOwnerId(),
                    item.getFileName(),
                    item.getMimeType(),
                    item.getSizeBytes(),
                    item.getStoragePath(),
                    item.getSha256(),
                    item.getUploadedBy().getId(),
                    item.getCreatedAt()
            );
        }
    }
}
