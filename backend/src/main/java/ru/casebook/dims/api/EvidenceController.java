package ru.casebook.dims.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.EvidenceDtos.EvidenceRequest;
import ru.casebook.dims.api.dto.EvidenceDtos.EvidenceResponse;
import ru.casebook.dims.api.dto.EvidenceDtos.EvidenceVersionResponse;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.service.CurrentUserService;
import ru.casebook.dims.service.EvidenceService;

import java.util.List;
import java.util.UUID;

@RestController
public class EvidenceController {
    private final EvidenceService evidenceService;
    private final CurrentUserService currentUserService;

    public EvidenceController(EvidenceService evidenceService, CurrentUserService currentUserService) {
        this.evidenceService = evidenceService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/cases/{caseId}/evidence")
    public List<EvidenceResponse> listByCase(@PathVariable UUID caseId) {
        return evidenceService.listByCase(caseId).stream().map(EvidenceResponse::from).toList();
    }

    @PostMapping("/api/cases/{caseId}/evidence")
    public EvidenceResponse create(@RequestHeader("X-User-Id") String userId, @PathVariable UUID caseId, @Valid @RequestBody EvidenceRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return EvidenceResponse.from(evidenceService.create(actor, caseId, request));
    }

    @GetMapping("/api/evidence/{id}")
    public EvidenceResponse get(@PathVariable UUID id) {
        return EvidenceResponse.from(evidenceService.get(id));
    }

    @PatchMapping("/api/evidence/{id}")
    public EvidenceResponse update(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody EvidenceRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return EvidenceResponse.from(evidenceService.update(actor, id, request));
    }

    @GetMapping("/api/evidence/{id}/versions")
    public List<EvidenceVersionResponse> versions(@PathVariable UUID id) {
        return evidenceService.versions(id).stream().map(EvidenceVersionResponse::from).toList();
    }
}
