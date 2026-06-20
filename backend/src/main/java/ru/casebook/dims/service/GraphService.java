package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.api.dto.GraphDtos.*;
import ru.casebook.dims.domain.*;
import ru.casebook.dims.repo.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GraphService {
    private static final int GRAPH_NODE_LIMIT = 200;

    private final CaseRepository cases;
    private final EvidenceRepository evidence;
    private final TaskRepository tasks;
    private final LabRequestRepository labs;
    private final ReportRepository reports;
    private final HypothesisRepository hypotheses;
    private final GraphEdgeRepository edges;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public GraphService(CaseRepository cases, EvidenceRepository evidence, TaskRepository tasks, LabRequestRepository labs, ReportRepository reports, HypothesisRepository hypotheses, GraphEdgeRepository edges, CurrentUserService currentUserService, AuditService auditService) {
        this.cases = cases;
        this.evidence = evidence;
        this.tasks = tasks;
        this.labs = labs;
        this.reports = reports;
        this.hypotheses = hypotheses;
        this.edges = edges;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    public GraphResponse graph(UUID caseId) {
        CaseFile caseFile = cases.findById(caseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Дело не найдено"));
        List<GraphNodeResponse> allNodes = new ArrayList<>();
        allNodes.add(new GraphNodeResponse(NodeType.CASE, caseFile.getId(), caseFile.getTitle(), caseFile.getStatus().name()));
        evidence.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.EVIDENCE, item.getId(), item.getName(), item.getStatus().name())));
        tasks.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.TASK, item.getId(), item.getTitle(), item.getStatus().name())));
        labs.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.LAB_REQUEST, item.getId(), item.getProfile(), item.getStatus().name())));
        reports.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.REPORT, item.getId(), item.getRegistrationNumber(), item.getStatus().name())));
        hypotheses.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.HYPOTHESIS, item.getId(), item.getTitle(), item.getConfidence().name())));

        boolean filtered = allNodes.size() > GRAPH_NODE_LIMIT;
        List<GraphNodeResponse> displayedNodes = filtered ? allNodes.stream().limit(GRAPH_NODE_LIMIT).toList() : allNodes;

        return new GraphResponse(
                displayedNodes,
                edges.findByCaseFileId(caseId).stream().map(GraphEdgeResponse::from).toList(),
                filtered,
                filtered ? "Граф превышает 200 узлов, отображение ограничено для производительности" : null
        );
    }

    @Transactional
    public GraphEdge createEdge(UserAccount actor, UUID caseId, GraphEdgeRequest request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE);
        CaseFile caseFile = cases.findById(caseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Дело не найдено"));
        if (edges.existsByCaseFileIdAndSourceTypeAndSourceIdAndTargetTypeAndTargetIdAndSemanticType(
                caseId, request.source().type(), request.source().id(), request.target().type(), request.target().id(), request.semanticType())) {
            throw new ApiException(HttpStatus.CONFLICT, "GRAPH_EDGE_DUPLICATE", "Такая связь уже существует");
        }
        Hypothesis hypothesis = hypotheses.save(new Hypothesis(caseFile, request.hypothesisTitle(), request.hypothesisText(), request.confidence(), actor));
        GraphEdge edge = edges.save(new GraphEdge(caseFile, request.source().type(), request.source().id(), request.target().type(), request.target().id(), request.semanticType(), request.confidence(), hypothesis, actor));
        auditService.record(actor, "GRAPH_EDGE_CREATED", "GraphEdge", edge.getId(), "{\"hypothesisId\":\"" + hypothesis.getId() + "\"}");
        return edge;
    }

    @Transactional
    public Hypothesis createHypothesis(UserAccount actor, UUID caseId, HypothesisRequest request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE, Role.ASSISTANT);
        CaseFile caseFile = cases.findById(caseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case not found"));
        Hypothesis created = hypotheses.save(new Hypothesis(caseFile, request.title(), request.text(), request.confidence(), actor));
        auditService.record(actor, "HYPOTHESIS_CREATED", "Hypothesis", created.getId(), "{\"caseId\":\"" + caseId + "\"}");
        return created;
    }

    public List<Hypothesis> hypotheses(UUID caseId) {
        return hypotheses.findByCaseFileId(caseId);
    }
}
