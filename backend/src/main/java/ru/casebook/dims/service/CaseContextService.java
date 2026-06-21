package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.api.dto.CaseContextDtos.*;
import ru.casebook.dims.domain.*;
import ru.casebook.dims.repo.*;
import java.util.List;
import java.util.UUID;

@Service
public class CaseContextService {
    private final CaseRepository cases; private final IncidentSceneRepository scenes; private final InterviewRepository interviews;
    private final CurrentUserService currentUsers; private final AuditService audit;
    public CaseContextService(CaseRepository cases, IncidentSceneRepository scenes, InterviewRepository interviews, CurrentUserService currentUsers, AuditService audit) {
        this.cases=cases; this.scenes=scenes; this.interviews=interviews; this.currentUsers=currentUsers; this.audit=audit;
    }
    private CaseFile activeCase(UUID id) {
        CaseFile c=cases.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"CASE_NOT_FOUND","Дело не найдено"));
        if (c.getStatus()==CaseStatus.CLOSED) throw new ApiException(HttpStatus.CONFLICT,"CASE_NOT_ACTIVE","Операция доступна только для активного дела");
        return c;
    }
    public List<IncidentScene> scenes(UUID caseId) { return scenes.findByCaseFileId(caseId); }
    public List<Interview> interviews(UUID caseId) { return interviews.findByCaseFileIdOrderByOccurredAtAsc(caseId); }
    @Transactional public IncidentScene addScene(UserAccount actor, UUID caseId, SceneRequest r) {
        currentUsers.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT, Role.INSPECTOR, Role.AGENT);
        IncidentScene item=scenes.save(new IncidentScene(activeCase(caseId),r.title(),r.description(),r.address(),r.latitude(),r.longitude(),actor));
        audit.record(actor,"INCIDENT_SCENE_CREATED","IncidentScene",item.getId(),"{\"caseId\":\""+caseId+"\"}"); return item;
    }
    @Transactional public Interview addInterview(UserAccount actor, UUID caseId, InterviewRequest r) {
        currentUsers.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT, Role.INSPECTOR);
        Interview item=interviews.save(new Interview(activeCase(caseId),r.interviewee(),r.occurredAt(),r.protocolText(),actor));
        audit.record(actor,"INTERVIEW_CREATED","Interview",item.getId(),"{\"caseId\":\""+caseId+"\"}"); return item;
    }
}
