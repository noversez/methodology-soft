package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.api.dto.EvidenceDtos.EvidenceRequest;
import ru.casebook.dims.domain.*;
import ru.casebook.dims.repo.CaseRepository;
import ru.casebook.dims.repo.EvidenceRepository;
import ru.casebook.dims.repo.EvidenceVersionRepository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class EvidenceService {
    private final EvidenceRepository evidence;
    private final EvidenceVersionRepository versions;
    private final CaseRepository cases;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public EvidenceService(EvidenceRepository evidence, EvidenceVersionRepository versions, CaseRepository cases, CurrentUserService currentUserService, AuditService auditService) {
        this.evidence = evidence;
        this.versions = versions;
        this.cases = cases;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    public List<Evidence> listByCase(UUID caseId) {
        return evidence.findByCaseFileId(caseId);
    }

    public Evidence get(UUID id) {
        return evidence.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Улика не найдена"));
    }

    @Transactional
    public Evidence create(UserAccount actor, UUID caseId, EvidenceRequest request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT, Role.INSPECTOR, Role.AGENT);
        CaseFile caseFile = cases.findById(caseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Дело не найдено"));
        if (caseFile.getStatus() == CaseStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "CASE_NOT_ACTIVE", "Нельзя добавлять улики в закрытое дело");
        }
        String year = String.valueOf(request.discoveryDateTime().atZone(ZoneOffset.UTC).getYear());
        String prefix = "EV-" + year + "-";
        Evidence created = evidence.save(new Evidence(
                caseFile,
                prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                request.name(),
                request.type(),
                request.importance(),
                request.description(),
                request.discoveryDateTime(),
                request.latitude(),
                request.longitude(),
                request.locationTitle(),
                actor
        ));
        versions.save(new EvidenceVersion(created, created.getDescription(), actor, 1));
        auditService.record(actor, "EVIDENCE_CREATED", "Evidence", created.getId(), "{\"caseId\":\"" + caseId + "\"}");
        return created;
    }

    @Transactional
    public Evidence update(UserAccount actor, UUID id, EvidenceRequest request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT, Role.INSPECTOR);
        Evidence item = get(id);
        if (item.getStatus() == EvidenceStatus.UNDER_EXAMINATION) {
            throw new ApiException(HttpStatus.CONFLICT, "EVIDENCE_LOCKED_FOR_EXAMINATION", "Улика заблокирована на время лабораторной экспертизы");
        }
        String oldDescription = item.getDescription();
        item.update(request.name(), request.type(), request.importance(), request.description(), request.discoveryDateTime(), request.latitude(), request.longitude(), request.locationTitle());
        if (!oldDescription.equals(request.description())) {
            versions.save(new EvidenceVersion(item, request.description(), actor, versions.maxVersionNumber(item.getId()) + 1));
        }
        auditService.record(actor, "EVIDENCE_UPDATED", "Evidence", item.getId(), "{}");
        return item;
    }

    public List<EvidenceVersion> versions(UUID evidenceId) {
        return versions.findByEvidenceIdOrderByVersionNumberAsc(evidenceId);
    }
}
