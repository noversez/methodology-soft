package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.api.dto.ReportDtos.ReportPreviewResponse;
import ru.casebook.dims.domain.*;
import ru.casebook.dims.repo.*;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {
    private final CaseRepository cases;
    private final EvidenceRepository evidence;
    private final LabRequestRepository labs;
    private final GraphEdgeRepository edges;
    private final HypothesisRepository hypotheses;
    private final ReportRepository reports;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public ReportService(CaseRepository cases, EvidenceRepository evidence, LabRequestRepository labs, GraphEdgeRepository edges, HypothesisRepository hypotheses, ReportRepository reports, CurrentUserService currentUserService, AuditService auditService) {
        this.cases = cases;
        this.evidence = evidence;
        this.labs = labs;
        this.edges = edges;
        this.hypotheses = hypotheses;
        this.reports = reports;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    public ReportPreviewResponse preview(UUID caseId) {
        CaseFile caseFile = getCase(caseId);
        boolean openLabs = labs.findByCaseFileId(caseId).stream().anyMatch(item -> item.getStatus() != LabRequestStatus.COMPLETED);
        return new ReportPreviewResponse(render(caseFile, openLabs), openLabs, openLabs ? "Есть незавершенные лабораторные запросы" : null);
    }

    @Transactional
    public ReportFile approve(UserAccount actor, UUID caseId, boolean force) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.INSPECTOR);
        CaseFile caseFile = getCase(caseId);
        if (caseFile.getTitle().isBlank() || caseFile.getDescription().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_REQUIRED_FIELDS_EMPTY", "Нельзя сформировать отчет: обязательные поля дела не заполнены");
        }
        boolean openLabs = labs.findByCaseFileId(caseId).stream().anyMatch(item -> item.getStatus() != LabRequestStatus.COMPLETED);
        if (openLabs && !force) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_HAS_OPEN_LAB_REQUESTS", "Есть незавершенные лабораторные запросы");
        }
        String year = String.valueOf(caseFile.getOpenedAt().atZone(ZoneOffset.UTC).getYear());
        String prefix = "REP-" + year + "-";
        String number = prefix + String.format("%03d", reports.countByRegistrationNumberStartingWith(prefix) + 1);
        String content = render(caseFile, openLabs);
        String hash = SecurityHash.sha256(content);
        ReportFile report = reports.save(new ReportFile(caseFile, number, "TEXT", ReportStatus.APPROVED, "storage/reports/" + number + ".txt", hash, actor, content));
        auditService.record(actor, "REPORT_APPROVED", "Report", report.getId(), "{\"hash\":\"" + hash + "\"}");
        return report;
    }

    public List<ReportFile> byCase(UUID caseId) {
        return reports.findByCaseFileId(caseId);
    }

    public ReportFile get(UUID id) {
        return reports.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Отчет не найден"));
    }

    private CaseFile getCase(UUID caseId) {
        return cases.findById(caseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Дело не найдено"));
    }

    private String render(CaseFile caseFile, boolean openLabs) {
        UUID caseId = caseFile.getId();
        StringBuilder out = new StringBuilder();
        out.append("Итоговый отчет по делу ").append(caseFile.getRegistrationNumber()).append('\n');
        out.append(caseFile.getTitle()).append("\n\n");
        out.append("Описание: ").append(caseFile.getDescription()).append("\n");
        out.append("Статус: ").append(caseFile.getStatus()).append("\n\n");
        out.append("Улики:\n");
        evidence.findByCaseFileId(caseId).forEach(item -> out.append("- ").append(item.getRegistrationNumber()).append(": ").append(item.getName()).append(" [").append(item.getStatus()).append("]\n"));
        out.append("\nЭкспертизы:\n");
        labs.findByCaseFileId(caseId).forEach(item -> out.append("- ").append(item.getProfile()).append(": ").append(item.getStatus()).append(item.getResultText() == null ? "" : " / результат внесен").append('\n'));
        if (openLabs) {
            out.append("- Данные ожидаются по незавершенным запросам.\n");
        }
        out.append("\nГипотезы:\n");
        hypotheses.findByCaseFileId(caseId).forEach(item -> out.append("- ").append(item.getTitle()).append(": ").append(item.getText()).append('\n'));
        out.append("\nСвязи графа: ").append(edges.findByCaseFileId(caseId).size()).append('\n');
        return new String(out.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
