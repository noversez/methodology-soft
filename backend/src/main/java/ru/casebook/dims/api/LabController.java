package ru.casebook.dims.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.LabDtos.*;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.service.CurrentUserService;
import ru.casebook.dims.service.LabService;

import java.util.List;
import java.util.UUID;

@RestController
public class LabController {
    private final LabService labService;
    private final CurrentUserService currentUserService;
    private final ru.casebook.dims.service.EntityDeletionService deletionService;

    public LabController(LabService labService, CurrentUserService currentUserService, ru.casebook.dims.service.EntityDeletionService deletionService) {
        this.labService = labService;
        this.currentUserService = currentUserService;
        this.deletionService = deletionService;
    }

    @GetMapping("/api/evidence/{evidenceId}/lab-requests")
    public List<LabResponse> list(@PathVariable UUID evidenceId) {
        return labService.listByEvidence(evidenceId).stream().map(LabResponse::from).toList();
    }

    @GetMapping("/api/cases/{caseId}/lab-requests")
    public List<LabResponse> listByCase(@PathVariable UUID caseId) {
        return labService.listByCase(caseId).stream().map(LabResponse::from).toList();
    }

    @GetMapping("/api/lab-requests/{id}")
    public LabResponse get(@PathVariable UUID id) {
        return LabResponse.from(labService.get(id));
    }

    @GetMapping("/api/lab-requests")
    public List<LabResponse> queue(@RequestHeader("X-User-Id") String userId) {
        UserAccount actor = currentUserService.requireUser(userId);
        return labService.queue(actor).stream().map(LabResponse::from).toList();
    }

    @PostMapping("/api/evidence/{evidenceId}/lab-requests")
    public LabResponse create(@RequestHeader("X-User-Id") String userId, @PathVariable UUID evidenceId, @Valid @RequestBody LabRequestCreate request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return LabResponse.from(labService.create(actor, evidenceId, request));
    }

    @PatchMapping("/api/lab-requests/{id}")
    public LabResponse update(@RequestHeader("X-User-Id") String userId,@PathVariable UUID id,@Valid @RequestBody LabRequestCreate request){return LabResponse.from(labService.update(currentUserService.requireUser(userId),id,request));}

    @PatchMapping("/api/lab-requests/{id}/status")
    public LabResponse status(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody LabStatusRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return LabResponse.from(labService.changeStatus(actor, id, request.status()));
    }

    @PostMapping("/api/lab-requests/{id}/result")
    public LabResponse result(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody LabResultRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return LabResponse.from(labService.complete(actor, id, request.resultText()));
    }
    @DeleteMapping("/api/lab-requests/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void delete(@RequestHeader("X-User-Id") String userId,@PathVariable UUID id){deletionService.deleteLab(currentUserService.requireUser(userId),id);}
}
