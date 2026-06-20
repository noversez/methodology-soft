package ru.casebook.dims.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.casebook.dims.domain.CaseFile;
import ru.casebook.dims.domain.CaseStatus;
import ru.casebook.dims.domain.Priority;

import java.time.Instant;
import java.util.UUID;

public final class CaseDtos {
    private CaseDtos() {
    }

    public record CaseRequest(
            @NotBlank String title,
            @NotNull Instant openedAt,
            @NotNull Priority priority,
            CaseStatus status,
            @NotBlank String description
    ) {
    }

    public record CaseResponse(
            UUID id,
            String registrationNumber,
            String title,
            Instant openedAt,
            Priority priority,
            CaseStatus status,
            String description,
            UUID createdBy,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CaseResponse from(CaseFile item) {
            return new CaseResponse(
                    item.getId(),
                    item.getRegistrationNumber(),
                    item.getTitle(),
                    item.getOpenedAt(),
                    item.getPriority(),
                    item.getStatus(),
                    item.getDescription(),
                    item.getCreatedBy().getId(),
                    item.getVersion(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            );
        }
    }
}
