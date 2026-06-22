package ru.casebook.dims.api.dto;

import jakarta.validation.constraints.*;
import ru.casebook.dims.domain.*;
import java.time.Instant;
import java.util.UUID;

public final class CaseContextDtos {
    private CaseContextDtos() {}
    public record SceneRequest(@NotBlank String title, @NotBlank String description, @NotBlank String address,
                               @DecimalMin("-90") @DecimalMax("90") Double latitude,
                               @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
    public record SceneResponse(UUID id, UUID caseId, String title, String description, String address, Double latitude, Double longitude, UUID createdBy, Instant createdAt) {
        public static SceneResponse from(IncidentScene s) { return new SceneResponse(s.getId(), s.getCaseFile().getId(), s.getTitle(), s.getDescription(), s.getAddress(), s.getLatitude(), s.getLongitude(), s.getCreatedBy().getId(), s.getCreatedAt()); }
    }
    public record InterviewRequest(@NotBlank String interviewee, @NotNull Instant occurredAt, @NotBlank String protocolText) {}
    public record InterviewResponse(UUID id, UUID caseId, String interviewee, Instant occurredAt, String protocolText, UUID createdBy, Instant createdAt) {
        public static InterviewResponse from(Interview i) { return new InterviewResponse(i.getId(), i.getCaseFile().getId(), i.getInterviewee(), i.getOccurredAt(), i.getProtocolText(), i.getCreatedBy().getId(), i.getCreatedAt()); }
    }
    public record ParticipantRequest(@NotNull UUID userId) {}
}
