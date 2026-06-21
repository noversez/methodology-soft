package ru.casebook.dims.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.casebook.dims.domain.Priority;
import ru.casebook.dims.domain.TaskItem;
import ru.casebook.dims.domain.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public final class TaskDtos {
    private TaskDtos() {
    }

    public record TaskRequest(
            @NotBlank String title,
            @NotBlank String description,
            @NotNull UUID assigneeId,
            @NotNull Priority priority,
            @NotNull Instant deadline
    ) {
    }

    public record TaskStatusRequest(@NotNull TaskStatus status, String resultText) {
    }

    public record TaskResultEvidenceRequest(
            @NotBlank String name,
            @NotBlank String type,
            @NotNull Priority importance,
            String locationTitle,
            Double latitude,
            Double longitude,
            Instant capturedAt
    ) {
        public TaskResultEvidenceRequest(String name, String type, Priority importance, String locationTitle) {
            this(name, type, importance, locationTitle, null, null, null);
        }
    }

    public record TaskResponse(
            UUID id,
            UUID caseId,
            String title,
            String description,
            UUID assigneeId,
            UUID createdBy,
            Priority priority,
            Instant deadline,
            TaskStatus status,
            String resultText,
            UUID resultEvidenceId,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static TaskResponse from(TaskItem item) {
            return new TaskResponse(
                    item.getId(),
                    item.getCaseFile().getId(),
                    item.getTitle(),
                    item.getDescription(),
                    item.getAssignee().getId(),
                    item.getCreatedBy().getId(),
                    item.getPriority(),
                    item.getDeadline(),
                    item.getStatus(),
                    item.getResultText(),
                    item.getResultEvidenceId(),
                    item.getVersion(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            );
        }
    }
}
