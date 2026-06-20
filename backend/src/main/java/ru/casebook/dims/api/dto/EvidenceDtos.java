package ru.casebook.dims.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.casebook.dims.domain.Evidence;
import ru.casebook.dims.domain.EvidenceStatus;
import ru.casebook.dims.domain.EvidenceVersion;
import ru.casebook.dims.domain.Priority;

import java.time.Instant;
import java.util.UUID;

public final class EvidenceDtos {
    private EvidenceDtos() {
    }

    public record EvidenceRequest(
            @NotBlank String name,
            @NotBlank String type,
            @NotNull Priority importance,
            @NotBlank String description,
            @NotNull Instant discoveryDateTime,
            Double latitude,
            Double longitude,
            String locationTitle
    ) {
    }

    public record EvidenceResponse(
            UUID id,
            UUID caseId,
            String registrationNumber,
            String name,
            String type,
            Priority importance,
            String description,
            Instant discoveryDateTime,
            Double latitude,
            Double longitude,
            String locationTitle,
            UUID responsibleUserId,
            EvidenceStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static EvidenceResponse from(Evidence item) {
            return new EvidenceResponse(
                    item.getId(),
                    item.getCaseFile().getId(),
                    item.getRegistrationNumber(),
                    item.getName(),
                    item.getType(),
                    item.getImportance(),
                    item.getDescription(),
                    item.getDiscoveryDateTime(),
                    item.getLatitude(),
                    item.getLongitude(),
                    item.getLocationTitle(),
                    item.getResponsibleUser().getId(),
                    item.getStatus(),
                    item.getVersion(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            );
        }
    }

    public record EvidenceVersionResponse(UUID id, UUID evidenceId, String descriptionSnapshot, UUID changedBy, Instant changedAt, long versionNumber) {
        public static EvidenceVersionResponse from(EvidenceVersion item) {
            return new EvidenceVersionResponse(
                    item.getId(),
                    item.getEvidence().getId(),
                    item.getDescriptionSnapshot(),
                    item.getChangedBy().getId(),
                    item.getChangedAt(),
                    item.getVersionNumber()
            );
        }
    }
}
