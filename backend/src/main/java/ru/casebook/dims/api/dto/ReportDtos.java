package ru.casebook.dims.api.dto;

import ru.casebook.dims.domain.ReportFile;
import ru.casebook.dims.domain.ReportStatus;

import java.time.Instant;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record ReportPreviewResponse(String content, boolean hasOpenLabRequests, String warning) {
    }

    public record ReportResponse(
            UUID id,
            UUID caseId,
            String registrationNumber,
            String format,
            ReportStatus status,
            String storagePath,
            String sha256,
            UUID approvedBy,
            Instant approvedAt,
            String content
    ) {
        public static ReportResponse from(ReportFile item) {
            return new ReportResponse(
                    item.getId(),
                    item.getCaseFile().getId(),
                    item.getRegistrationNumber(),
                    item.getFormat(),
                    item.getStatus(),
                    item.getStoragePath(),
                    item.getSha256(),
                    item.getApprovedBy().getId(),
                    item.getApprovedAt(),
                    item.getContent()
            );
        }
    }
}
