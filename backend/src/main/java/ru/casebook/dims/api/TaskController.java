package ru.casebook.dims.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.TaskDtos.TaskRequest;
import ru.casebook.dims.api.dto.TaskDtos.TaskResponse;
import ru.casebook.dims.api.dto.TaskDtos.TaskStatusRequest;
import ru.casebook.dims.api.dto.TaskDtos.TaskResultEvidenceRequest;
import ru.casebook.dims.api.dto.EvidenceDtos.EvidenceResponse;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.service.CurrentUserService;
import ru.casebook.dims.service.TaskService;

import java.util.List;
import java.util.UUID;

@RestController
public class TaskController {
    private final TaskService taskService;
    private final CurrentUserService currentUserService;

    public TaskController(TaskService taskService, CurrentUserService currentUserService) {
        this.taskService = taskService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/cases/{caseId}/tasks")
    public List<TaskResponse> listByCase(@PathVariable UUID caseId) {
        return taskService.listByCase(caseId).stream().map(TaskResponse::from).toList();
    }

    @PostMapping("/api/cases/{caseId}/tasks")
    public TaskResponse create(@RequestHeader("X-User-Id") String userId, @PathVariable UUID caseId, @Valid @RequestBody TaskRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return TaskResponse.from(taskService.create(actor, caseId, request));
    }

    @GetMapping("/api/tasks/my")
    public List<TaskResponse> my(@RequestHeader("X-User-Id") String userId) {
        UserAccount actor = currentUserService.requireUser(userId);
        return taskService.myTasks(actor).stream().map(TaskResponse::from).toList();
    }

    @PatchMapping("/api/tasks/{id}")
    public TaskResponse update(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody TaskRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return TaskResponse.from(taskService.update(actor, id, request));
    }

    @PatchMapping("/api/tasks/{id}/status")
    public TaskResponse status(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody TaskStatusRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return TaskResponse.from(taskService.updateStatus(actor, id, request));
    }

    @PostMapping("/api/tasks/{id}/result/evidence")
    public EvidenceResponse resultEvidence(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody TaskResultEvidenceRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return EvidenceResponse.from(taskService.createResultEvidence(actor, id, request));
    }
}
