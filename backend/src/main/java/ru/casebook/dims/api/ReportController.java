package ru.casebook.dims.api;

import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.ReportDtos.ReportPreviewResponse;
import ru.casebook.dims.api.dto.ReportDtos.ReportResponse;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.service.CurrentUserService;
import ru.casebook.dims.service.ReportService;

import java.util.List;
import java.util.UUID;

@RestController
public class ReportController {
    private final ReportService reportService;
    private final CurrentUserService currentUserService;

    public ReportController(ReportService reportService, CurrentUserService currentUserService) {
        this.reportService = reportService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/cases/{caseId}/reports/preview")
    public ReportPreviewResponse preview(@PathVariable UUID caseId, @RequestParam(defaultValue = "FULL") String template) {
        return reportService.preview(caseId, template);
    }

    @PostMapping("/api/cases/{caseId}/reports")
    public ReportResponse approve(@RequestHeader("X-User-Id") String userId, @PathVariable UUID caseId, @RequestParam(defaultValue = "false") boolean force, @RequestParam(defaultValue = "FULL") String template) {
        UserAccount actor = currentUserService.requireUser(userId);
        return ReportResponse.from(reportService.approve(actor, caseId, force, template));
    }

    @GetMapping("/api/cases/{caseId}/reports")
    public List<ReportResponse> byCase(@PathVariable UUID caseId) {
        return reportService.byCase(caseId).stream().map(ReportResponse::from).toList();
    }

    @GetMapping("/api/reports/{id}")
    public ReportResponse get(@PathVariable UUID id) {
        return ReportResponse.from(reportService.get(id));
    }

    @GetMapping(value = "/api/reports/{id}/download", produces = "text/plain; charset=UTF-8")
    public String download(@PathVariable UUID id) {
        return reportService.get(id).getContent();
    }
}
