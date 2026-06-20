package ru.casebook.dims.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.CaseDtos.CaseRequest;
import ru.casebook.dims.api.dto.CaseDtos.CaseResponse;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.service.CaseService;
import ru.casebook.dims.service.CurrentUserService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
public class CaseController {
    private final CaseService caseService;
    private final CurrentUserService currentUserService;

    public CaseController(CaseService caseService, CurrentUserService currentUserService) {
        this.caseService = caseService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<CaseResponse> list() {
        return caseService.list().stream().map(CaseResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CaseResponse get(@PathVariable UUID id) {
        return CaseResponse.from(caseService.get(id));
    }

    @PostMapping
    public CaseResponse create(@RequestHeader("X-User-Id") String userId, @Valid @RequestBody CaseRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return CaseResponse.from(caseService.create(actor, request));
    }

    @PatchMapping("/{id}")
    public CaseResponse update(@RequestHeader("X-User-Id") String userId, @PathVariable UUID id, @Valid @RequestBody CaseRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return CaseResponse.from(caseService.update(actor, id, request));
    }
}
