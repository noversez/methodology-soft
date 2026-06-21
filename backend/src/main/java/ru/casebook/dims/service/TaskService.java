package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.api.dto.TaskDtos.TaskRequest;
import ru.casebook.dims.api.dto.TaskDtos.TaskStatusRequest;
import ru.casebook.dims.api.dto.TaskDtos.TaskResultEvidenceRequest;
import ru.casebook.dims.api.dto.EvidenceDtos.EvidenceRequest;
import ru.casebook.dims.domain.CaseFile;
import ru.casebook.dims.domain.Evidence;
import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.TaskItem;
import ru.casebook.dims.domain.TaskStatus;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.repo.CaseRepository;
import ru.casebook.dims.repo.TaskRepository;
import ru.casebook.dims.repo.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final CaseRepository cases;
    private final UserRepository users;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final EvidenceService evidenceService;

    public TaskService(TaskRepository tasks, CaseRepository cases, UserRepository users, CurrentUserService currentUserService, NotificationService notificationService, AuditService auditService, EvidenceService evidenceService) {
        this.tasks = tasks;
        this.cases = cases;
        this.users = users;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.evidenceService = evidenceService;
    }

    public List<TaskItem> listByCase(UUID caseId) {
        return tasks.findByCaseFileId(caseId);
    }

    public List<TaskItem> myTasks(UserAccount actor) {
        return tasks.findByAssigneeId(actor.getId());
    }

    @Transactional
    public TaskItem create(UserAccount actor, UUID caseId, TaskRequest request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE);
        CaseFile caseFile = cases.findById(caseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case not found"));
        UserAccount assignee = resolveAssignee(request.assigneeId());
        validateDeadline(caseFile, request.deadline());

        TaskItem created = tasks.save(new TaskItem(caseFile, request.title(), request.description(), assignee, actor, request.priority(), request.deadline()));
        notificationService.notify(assignee, "TASK_ASSIGNED", "{\"taskId\":\"" + created.getId() + "\"}");
        auditService.record(actor, "TASK_CREATED", "Task", created.getId(), "{\"caseId\":\"" + caseId + "\"}");
        return created;
    }

    @Transactional
    public TaskItem update(UserAccount actor, UUID id, TaskRequest request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE);
        TaskItem task = tasks.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));
        UserAccount oldAssignee = task.getAssignee();
        UserAccount assignee = resolveAssignee(request.assigneeId());
        validateDeadline(task.getCaseFile(), request.deadline());

        task.updateDetails(request.title(), request.description(), assignee, request.priority(), request.deadline());
        if (!oldAssignee.getId().equals(assignee.getId())) {
            notificationService.notify(assignee, "TASK_REASSIGNED", "{\"taskId\":\"" + task.getId() + "\"}");
            auditService.record(actor, "TASK_REASSIGNED", "Task", task.getId(), "{\"assigneeId\":\"" + assignee.getId() + "\"}");
        } else {
            auditService.record(actor, "TASK_UPDATED", "Task", task.getId(), "{}");
        }
        return task;
    }

    @Transactional
    public TaskItem updateStatus(UserAccount actor, UUID id, TaskStatusRequest request) {
        TaskItem task = tasks.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));
        if (!task.getAssignee().getId().equals(actor.getId()) && actor.getRole() != Role.DETECTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only assignee or lead detective can update task status");
        }
        if (request.status() == TaskStatus.DONE && (request.resultText() == null || request.resultText().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_RESULT_REQUIRED", "Result is required to complete the task");
        }
        task.updateStatus(request.status(), request.resultText());
        auditService.record(actor, "TASK_STATUS_CHANGED", "Task", task.getId(), "{\"status\":\"" + request.status() + "\"}");
        return task;
    }

    @Transactional
    public Evidence createResultEvidence(UserAccount actor, UUID id, TaskResultEvidenceRequest request) {
        TaskItem task = tasks.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));
        if (!task.getAssignee().getId().equals(actor.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Only assignee can register task result as evidence");
        }
        if (task.getStatus() != TaskStatus.DONE || task.getResultText() == null || task.getResultText().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_RESULT_REQUIRED", "Complete the task with a result before registering evidence");
        }
        if (task.getResultEvidenceId() != null) {
            return evidenceService.get(task.getResultEvidenceId());
        }
        Evidence created = evidenceService.create(actor, task.getCaseFile().getId(), new EvidenceRequest(
                request.name(), request.type(), request.importance(), task.getResultText(), request.capturedAt() == null ? Instant.now() : request.capturedAt(), request.latitude(), request.longitude(), request.locationTitle()
        ));
        task.linkResultEvidence(created.getId());
        auditService.record(actor, "TASK_RESULT_LINKED_TO_EVIDENCE", "Task", task.getId(), "{\"evidenceId\":\"" + created.getId() + "\"}");
        return created;
    }

    private UserAccount resolveAssignee(UUID assigneeId) {
        if (assigneeId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSIGNEE_REQUIRED", "Assignee is required");
        }
        UserAccount assignee = users.findById(assigneeId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ASSIGNEE_NOT_FOUND", "Assignee not found"));
        if (assignee.getRole() != Role.AGENT && assignee.getRole() != Role.ASSISTANT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEE_ROLE", "Assignee must be Agent or Assistant");
        }
        return assignee;
    }

    private void validateDeadline(CaseFile caseFile, Instant deadline) {
        if (deadline == null || deadline.isBefore(Instant.now()) || deadline.isBefore(caseFile.getOpenedAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE", "Deadline cannot be in the past or before case opening");
        }
    }
}
