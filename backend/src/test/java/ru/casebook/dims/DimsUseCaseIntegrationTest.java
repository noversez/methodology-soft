package ru.casebook.dims;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.api.dto.CaseDtos.CaseRequest;
import ru.casebook.dims.api.dto.CaseContextDtos.SceneRequest;
import ru.casebook.dims.api.dto.EvidenceDtos.EvidenceRequest;
import ru.casebook.dims.api.dto.GraphDtos.GraphEdgeRequest;
import ru.casebook.dims.api.dto.GraphDtos.HypothesisRequest;
import ru.casebook.dims.api.dto.GraphDtos.NodeRef;
import ru.casebook.dims.api.dto.LabDtos.LabRequestCreate;
import ru.casebook.dims.api.dto.ReportDtos.ReportPreviewResponse;
import ru.casebook.dims.api.dto.TaskDtos.TaskRequest;
import ru.casebook.dims.api.dto.TaskDtos.TaskStatusRequest;
import ru.casebook.dims.api.dto.TaskDtos.TaskResultEvidenceRequest;
import ru.casebook.dims.domain.*;
import ru.casebook.dims.repo.AuditLogRepository;
import ru.casebook.dims.repo.NotificationRepository;
import ru.casebook.dims.repo.UserRepository;
import ru.casebook.dims.service.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DimsUseCaseIntegrationTest {
    @Autowired CaseService caseService;
    @Autowired EvidenceService evidenceService;
    @Autowired TaskService taskService;
    @Autowired LabService labService;
    @Autowired GraphService graphService;
    @Autowired ReportService reportService;
    @Autowired AttachmentService attachmentService;
    @Autowired AuthSessionService authSessionService;
    @Autowired CaseContextService caseContextService;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired AuditLogRepository auditLogs;

    @Test
    void caseCreationIsRestrictedAndAudited() {
        UserAccount detective = user("sherlock");
        UserAccount agent = user("agent");

        CaseFile created = createCase(detective, "Restricted case");

        assertThat(created.getRegistrationNumber()).startsWith("CASE-");
        assertThat(created.getStatus()).isEqualTo(CaseStatus.NEW);
        assertThat(auditLogs.findAll()).anyMatch(log -> "CASE_CREATED".equals(log.getAction()) && created.getId().equals(log.getEntityId()));
        assertThatThrownBy(() -> createCase(agent, "Forbidden case"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void uc01UsesAuthenticatedSessionAndPersistsIncidentScene() {
        UserAccount detective = user("sherlock");
        String token = authSessionService.create(detective);
        assertThat(authSessionService.require(token).getId()).isEqualTo(detective.getId());
        assertThatThrownBy(() -> authSessionService.require("invalid-token"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("AUTH_REQUIRED");

        CaseFile caseFile = createCase(detective, "Scene case");
        IncidentScene scene = caseContextService.addScene(detective, caseFile.getId(), new SceneRequest(
                "Основное место", "Первичный осмотр помещения", "Бейкер-стрит, 221B", 51.5237, -0.1585
        ));

        assertThat(scene.getCaseFile().getId()).isEqualTo(caseFile.getId());
        assertThat(caseContextService.scenes(caseFile.getId())).extracting(IncidentScene::getId).contains(scene.getId());
        assertThat(auditLogs.findAll()).anyMatch(log -> "INCIDENT_SCENE_CREATED".equals(log.getAction()) && scene.getId().equals(log.getEntityId()));
    }

    @Test
    void evidenceUpdateCreatesVersionsAndAttachmentSizeIsValidated() {
        UserAccount detective = user("sherlock");
        CaseFile caseFile = createCase(detective, "Evidence case");
        Evidence evidence = createEvidence(detective, caseFile, "Initial description");

        Evidence updated = evidenceService.update(detective, evidence.getId(), new EvidenceRequest(
                "Fiber sample updated",
                "fiber",
                Priority.HIGH,
                "Changed description",
                Instant.now(),
                55.751244,
                37.618423,
                "Moscow"
        ));

        assertThat(updated.getDescription()).isEqualTo("Changed description");
        assertThat(evidenceService.versions(evidence.getId())).hasSize(2);
        assertThat(attachmentService.register(detective, "Evidence", evidence.getId(), "photo.jpg", "image/jpeg", 1024).getSha256()).isNotBlank();
        assertThatThrownBy(() -> attachmentService.register(detective, "Evidence", evidence.getId(), "huge.bin", "application/octet-stream", 21L * 1024L * 1024L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void tasksValidateAssigneeDeadlineStatusAndReassignment() {
        UserAccount detective = user("sherlock");
        UserAccount agent = user("agent");
        UserAccount assistant = user("watson");
        UserAccount admin = user("admin");
        CaseFile caseFile = createCase(detective, "Task case");

        TaskItem task = taskService.create(detective, caseFile.getId(), taskRequest("Inspect scene", agent.getId(), Instant.now().plusSeconds(86_400)));
        TaskItem started = taskService.updateStatus(agent, task.getId(), new TaskStatusRequest(TaskStatus.IN_PROGRESS, null));
        assertThat(started.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        taskService.updateStatus(agent, task.getId(), new TaskStatusRequest(TaskStatus.DONE, "Field result with a photographed trace"));
        TaskResultEvidenceRequest evidenceRequest = new TaskResultEvidenceRequest("Task trace", "digital", Priority.MEDIUM, "Station");
        Evidence resultEvidence = taskService.createResultEvidence(agent, task.getId(), evidenceRequest);
        assertThat(taskService.createResultEvidence(agent, task.getId(), evidenceRequest).getId()).isEqualTo(resultEvidence.getId());
        assertThat(evidenceService.listByCase(caseFile.getId())).filteredOn(item -> item.getId().equals(resultEvidence.getId())).hasSize(1);

        TaskItem reassigned = taskService.update(detective, task.getId(), taskRequest("Inspect scene updated", assistant.getId(), Instant.now().plusSeconds(172_800)));
        assertThat(reassigned.getAssignee().getId()).isEqualTo(assistant.getId());
        assertThat(reassigned.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDesc(assistant.getId())).anyMatch(item -> "TASK_REASSIGNED".equals(item.getType()));
        assertThatThrownBy(() -> taskService.create(detective, caseFile.getId(), taskRequest("Past", agent.getId(), Instant.now().minusSeconds(60))))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_DEADLINE");
        assertThatThrownBy(() -> taskService.create(detective, caseFile.getId(), taskRequest("Bad role", admin.getId(), Instant.now().plusSeconds(86_400))))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_ASSIGNEE_ROLE");
    }

    @Test
    void labRequestsBlockDuplicatesRequireLongResultAndDriveReportFlow() {
        UserAccount detective = user("sherlock");
        UserAccount lab = user("lab");
        CaseFile caseFile = createCase(detective, "Lab case");
        Evidence evidence = createEvidence(detective, caseFile, "Needs lab work");

        LabRequest request = labService.create(detective, evidence.getId(), new LabRequestCreate("DNA", "Compare trace sample", Instant.now().plusSeconds(86_400)));
        assertThat(request.getStatus()).isEqualTo(LabRequestStatus.CREATED);
        assertThat(labService.queue(lab)).extracting(LabRequest::getId).contains(request.getId());
        assertThat(evidenceService.get(evidence.getId()).getStatus()).isEqualTo(EvidenceStatus.UNDER_EXAMINATION);
        assertThat(labService.get(request.getId()).getId()).isEqualTo(request.getId());
        assertThatThrownBy(() -> labService.create(detective, evidence.getId(), new LabRequestCreate("DNA", "Duplicate", Instant.now().plusSeconds(86_400))))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("ACTIVE_LAB_REQUEST_EXISTS");
        assertThatThrownBy(() -> labService.complete(lab, request.getId(), "too short"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("LAB_RESULT_TOO_SHORT");

        ReportPreviewResponse preview = reportService.preview(caseFile.getId());
        assertThat(preview.hasOpenLabRequests()).isTrue();
        assertThatThrownBy(() -> reportService.approve(detective, caseFile.getId(), false))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("REPORT_HAS_OPEN_LAB_REQUESTS");

        labService.complete(lab, request.getId(), "x".repeat(10_000));
        ReportFile report = reportService.approve(detective, caseFile.getId(), false);
        assertThat(report.getRegistrationNumber()).startsWith("REP-");
        assertThat(report.getSha256()).hasSize(64);
    }

    @Test
    void graphEdgesAndStandaloneHypothesesAreCreatedWithDuplicateProtection() {
        UserAccount detective = user("sherlock");
        CaseFile caseFile = createCase(detective, "Graph case");
        Evidence evidence = createEvidence(detective, caseFile, "Graph evidence");

        Hypothesis hypothesis = graphService.createHypothesis(detective, caseFile.getId(), new HypothesisRequest("Motive", "Financial motive", Confidence.MEDIUM));
        GraphEdgeRequest edgeRequest = new GraphEdgeRequest(
                new NodeRef(NodeType.CASE, caseFile.getId()),
                new NodeRef(NodeType.EVIDENCE, evidence.getId()),
                "supports",
                Confidence.HIGH,
                "Evidence link",
                "The evidence supports the case theory"
        );
        GraphEdge edge = graphService.createEdge(detective, caseFile.getId(), edgeRequest);

        assertThat(hypothesis.getId()).isNotNull();
        assertThat(graphService.graph(caseFile.getId()).nodes()).isNotEmpty();
        assertThat(edge.getHypothesis()).isNotNull();
        assertThatThrownBy(() -> graphService.createEdge(detective, caseFile.getId(), edgeRequest))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("GRAPH_EDGE_DUPLICATE");
    }

    private UserAccount user(String login) {
        return users.findByLogin(login).orElseThrow();
    }

    private CaseFile createCase(UserAccount actor, String title) {
        return caseService.create(actor, new CaseRequest(title, Instant.now(), Priority.MEDIUM, null, "Description for " + title));
    }

    private Evidence createEvidence(UserAccount actor, CaseFile caseFile, String description) {
        return evidenceService.create(actor, caseFile.getId(), new EvidenceRequest(
                "Fiber sample",
                "fiber",
                Priority.MEDIUM,
                description,
                Instant.now(),
                55.751244,
                37.618423,
                "Moscow"
        ));
    }

    private TaskRequest taskRequest(String title, java.util.UUID assigneeId, Instant deadline) {
        return new TaskRequest(title, "Task description", assigneeId, Priority.MEDIUM, deadline);
    }
}
