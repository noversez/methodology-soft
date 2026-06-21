package ru.casebook.dims.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.casebook.dims.domain.LabRequest;
import ru.casebook.dims.domain.LabRequestStatus;

import java.time.Instant;
import java.util.UUID;

public final class LabDtos {
    private LabDtos() {
    }

    public record LabRequestCreate(
            @NotBlank String profile,
            @NotBlank String questions,
            @NotNull Instant desiredDueDate
    ) {
    }

    public record LabStatusRequest(@NotNull LabRequestStatus status) {
    }

    public record LabResultRequest(@NotBlank String resultText) {
    }

    public record LabResponse(
            UUID id,
            UUID caseId,
            String registrationNumber,
            UUID evidenceId,
            String profile,
            String questions,
            Instant desiredDueDate,
            LabRequestStatus status,
            UUID requesterId,
            UUID labAssigneeId,
            String resultText,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static LabResponse from(LabRequest item) {
            return new LabResponse(
                    item.getId(),
                    item.getCaseFile().getId(),
                    item.getRegistrationNumber(),
                    item.getEvidence().getId(),
                    item.getProfile(),
                    item.getQuestions(),
                    item.getDesiredDueDate(),
                    item.getStatus(),
                    item.getRequester().getId(),
                    item.getLabAssignee() == null ? null : item.getLabAssignee().getId(),
                    item.getResultText(),
                    item.getVersion(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            );
        }
    }
}
