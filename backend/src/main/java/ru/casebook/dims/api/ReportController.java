package ru.casebook.dims.api;

import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.ReportDtos.ReportPreviewResponse;
import ru.casebook.dims.api.dto.ReportDtos.ReportResponse;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.service.CurrentUserService;
import ru.casebook.dims.service.ReportService;

import java.util.List;
import java.util.UUID;
import org.springframework.http.*;

@RestController
public class ReportController {
    private final ReportService reportService;
    private final CurrentUserService currentUserService;

    public ReportController(ReportService reportService, CurrentUserService currentUserService) {
        this.reportService = reportService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/cases/{caseId}/reports/preview")
    public ReportPreviewResponse preview(@RequestHeader("X-User-Id") String userId, @PathVariable UUID caseId, @RequestParam(defaultValue = "FULL") String template, @RequestParam(defaultValue = "TEXT") String format) {
        return reportService.preview(currentUserService.requireUser(userId), caseId, template, format);
    }

    @PostMapping("/api/cases/{caseId}/reports")
    public ReportResponse approve(@RequestHeader("X-User-Id") String userId, @PathVariable UUID caseId, @RequestParam(defaultValue = "false") boolean force, @RequestParam(defaultValue = "FULL") String template, @RequestParam(defaultValue = "TEXT") String format) {
        UserAccount actor = currentUserService.requireUser(userId);
        return ReportResponse.from(reportService.approve(actor, caseId, force, template, format));
    }

    @GetMapping("/api/cases/{caseId}/reports")
    public List<ReportResponse> byCase(@PathVariable UUID caseId) {
        return reportService.byCase(caseId).stream().map(ReportResponse::from).toList();
    }

    @GetMapping("/api/reports/{id}")
    public ReportResponse get(@PathVariable UUID id) {
        return ReportResponse.from(reportService.get(id));
    }

    @GetMapping("/api/reports/{id}/download")
    public ResponseEntity<byte[]> download(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id) {
        var file=reportService.download(currentUserService.requireUser(userId),id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType()+"; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+file.fileName()+"\"").body(file.content());
    }
}
