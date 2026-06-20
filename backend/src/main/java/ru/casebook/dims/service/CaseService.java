package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.api.dto.CaseDtos.CaseRequest;
import ru.casebook.dims.domain.CaseFile;
import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.repo.CaseRepository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class CaseService {
    private final CaseRepository cases;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public CaseService(CaseRepository cases, CurrentUserService currentUserService, AuditService auditService) {
        this.cases = cases;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    public List<CaseFile> list() {
        return cases.findAll();
    }

    public CaseFile get(UUID id) {
        return cases.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Дело не найдено"));
    }

    @Transactional
    public CaseFile create(UserAccount actor, CaseRequest request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.INSPECTOR);
        String year = String.valueOf(request.openedAt().atZone(ZoneOffset.UTC).getYear());
        String prefix = "CASE-" + year + "-";
        long next = cases.countByRegistrationNumberStartingWith(prefix) + 1;
        String number = prefix + String.format("%03d", next);
        CaseFile created = cases.save(new CaseFile(number, request.title(), request.openedAt(), request.priority(), request.description(), actor));
        auditService.record(actor, "CASE_CREATED", "Case", created.getId(), "{\"registrationNumber\":\"" + number + "\"}");
        return created;
    }

    @Transactional
    public CaseFile update(UserAccount actor, UUID id, CaseRequest request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.INSPECTOR);
        CaseFile item = get(id);
        item.update(request.title(), request.openedAt(), request.priority(), request.status() == null ? item.getStatus() : request.status(), request.description());
        auditService.record(actor, "CASE_UPDATED", "Case", item.getId(), "{}");
        return item;
    }
}
