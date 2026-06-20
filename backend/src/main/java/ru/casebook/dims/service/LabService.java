package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.api.dto.LabDtos.LabRequestCreate;
import ru.casebook.dims.domain.*;
import ru.casebook.dims.repo.EvidenceRepository;
import ru.casebook.dims.repo.LabRequestRepository;
import ru.casebook.dims.repo.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class LabService {
    private final LabRequestRepository labs;
    private final EvidenceRepository evidence;
    private final UserRepository users;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public LabService(LabRequestRepository labs, EvidenceRepository evidence, UserRepository users, CurrentUserService currentUserService, NotificationService notificationService, AuditService auditService) {
        this.labs = labs;
        this.evidence = evidence;
        this.users = users;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public List<LabRequest> listByEvidence(UUID evidenceId) {
        return labs.findByEvidenceId(evidenceId);
    }

    public List<LabRequest> queue(UserAccount actor) {
        currentUserService.requireAnyRole(actor, Role.LAB_ANALYST);
        return labs.findByLabAssigneeIdOrderByDesiredDueDateAsc(actor.getId());
    }

    public LabRequest get(UUID id) {
        return labs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LAB_REQUEST_NOT_FOUND", "Lab request not found"));
    }

    @Transactional
    public LabRequest create(UserAccount actor, UUID evidenceId, LabRequestCreate request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT);
        Evidence item = evidence.findById(evidenceId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Улика не найдена"));
        if (labs.existsByEvidenceIdAndStatusNot(evidenceId, LabRequestStatus.COMPLETED)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_LAB_REQUEST_EXISTS", "По этой улике уже есть активный лабораторный запрос");
        }
        UserAccount labAssignee = users.findByRoleAndActiveTrue(Role.LAB_ANALYST).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "LAB_ASSIGNEE_NOT_FOUND", "Нет активного лаборанта"));
        LabRequest created = labs.save(new LabRequest(item.getCaseFile(), item, request.profile(), request.questions(), request.desiredDueDate(), actor, labAssignee));
        item.setStatus(EvidenceStatus.UNDER_EXAMINATION);
        notificationService.notify(labAssignee, "LAB_REQUEST_ASSIGNED", "{\"labRequestId\":\"" + created.getId() + "\"}");
        auditService.record(actor, "LAB_REQUEST_CREATED", "LabRequest", created.getId(), "{\"evidenceId\":\"" + evidenceId + "\"}");
        return created;
    }

    @Transactional
    public LabRequest changeStatus(UserAccount actor, UUID id, LabRequestStatus status) {
        currentUserService.requireAnyRole(actor, Role.LAB_ANALYST);
        LabRequest lab = labs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LAB_REQUEST_NOT_FOUND", "Лабораторный запрос не найден"));
        lab.setStatus(status);
        notificationService.notify(lab.getRequester(), "LAB_STATUS_CHANGED", "{\"labRequestId\":\"" + lab.getId() + "\",\"status\":\"" + status + "\"}");
        auditService.record(actor, "LAB_REQUEST_STATUS_CHANGED", "LabRequest", lab.getId(), "{\"status\":\"" + status + "\"}");
        return lab;
    }

    @Transactional
    public LabRequest complete(UserAccount actor, UUID id, String resultText) {
        currentUserService.requireAnyRole(actor, Role.LAB_ANALYST);
        if (resultText.length() < 10_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LAB_RESULT_TOO_SHORT", "Заключение должно содержать не менее 10 000 знаков");
        }
        LabRequest lab = labs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LAB_REQUEST_NOT_FOUND", "Лабораторный запрос не найден"));
        lab.complete(resultText);
        lab.getEvidence().setStatus(EvidenceStatus.EXAMINATION_COMPLETED);
        notificationService.notify(lab.getRequester(), "LAB_RESULT_READY", "{\"labRequestId\":\"" + lab.getId() + "\"}");
        auditService.record(actor, "LAB_REQUEST_COMPLETED", "LabRequest", lab.getId(), "{}");
        return lab;
    }
}
