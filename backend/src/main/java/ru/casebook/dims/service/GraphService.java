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
    private final IncidentSceneRepository scenes;
    private final HypothesisRepository hypotheses;
    private final GraphEdgeRepository edges;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    public GraphService(CaseRepository cases, EvidenceRepository evidence, TaskRepository tasks, LabRequestRepository labs, ReportRepository reports, IncidentSceneRepository scenes, HypothesisRepository hypotheses, GraphEdgeRepository edges, CurrentUserService currentUserService, AuditService auditService) {
        this.cases = cases;
        this.evidence = evidence;
        this.tasks = tasks;
        this.labs = labs;
        this.reports = reports;
        this.scenes = scenes;
        this.hypotheses = hypotheses;
        this.edges = edges;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public GraphResponse graph(UUID caseId) {
        CaseFile caseFile = cases.findById(caseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Дело не найдено"));
        List<GraphNodeResponse> allNodes = new ArrayList<>();
        allNodes.add(new GraphNodeResponse(NodeType.CASE, caseFile.getId(), caseFile.getTitle(), caseFile.getStatus().name(), caseFile.getVersion()));
        evidence.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.EVIDENCE, item.getId(), item.getName(), item.getStatus().name(), item.getVersion())));
        tasks.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.TASK, item.getId(), item.getTitle(), item.getStatus().name(), item.getVersion())));
        labs.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.LAB_REQUEST, item.getId(), item.getProfile(), item.getStatus().name(), item.getVersion())));
        reports.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.REPORT, item.getId(), item.getRegistrationNumber(), item.getStatus().name(), 0)));
        scenes.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.LOCATION, item.getId(), item.getTitle(), item.getAddress(), 0)));
        hypotheses.findByCaseFileId(caseId).forEach(item -> allNodes.add(new GraphNodeResponse(NodeType.HYPOTHESIS, item.getId(), item.getTitle(), item.getConfidence().name(), 0)));
        boolean filtered = allNodes.size() > GRAPH_NODE_LIMIT;
        List<GraphNodeResponse> displayedNodes = filtered ? allNodes.stream().limit(GRAPH_NODE_LIMIT).toList() : allNodes;

        return new GraphResponse(
                displayedNodes,
                edges.findByCaseFileId(caseId).stream().map(GraphEdgeResponse::from).toList(),
                filtered,
                filtered ? "Граф превышает 200 узлов, отображение ограничено для производительности" : null,
                caseFile.getGraphRevision()
        );
    }

    @Transactional
    public GraphEdge createEdge(UserAccount actor, UUID caseId, GraphEdgeRequest request) {
        currentUserService.requireAnyRole(actor, Role.DETECTIVE);
        CaseFile caseFile = cases.findById(caseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Дело не найдено"));
        if (caseFile.getStatus() == CaseStatus.CLOSED) throw new ApiException(HttpStatus.CONFLICT, "CASE_NOT_ACTIVE", "Граф закрытого дела недоступен для изменения");
        if (request.expectedGraphRevision() != null && request.expectedGraphRevision() != caseFile.getGraphRevision()) {
            throw new ApiException(HttpStatus.CONFLICT, "GRAPH_STALE", "Граф был изменен другим пользователем и обновлен");
        }
        if (request.source().equals(request.target())) throw new ApiException(HttpStatus.BAD_REQUEST, "GRAPH_SELF_LINK", "Нельзя связать объект с самим собой");
        requireNodeInCase(caseId, request.source());
        requireNodeInCase(caseId, request.target());
        requireNodeVersion(request.source());
        requireNodeVersion(request.target());
        List<GraphEdge> currentEdges = edges.findByCaseFileId(caseId);
        if (currentEdges.stream().anyMatch(edge -> sameEdge(edge, request) && normalize(edge.getSemanticType()).equals(normalize(request.semanticType())))) {
            throw new ApiException(HttpStatus.CONFLICT, "GRAPH_EDGE_DUPLICATE", "Такая связь уже существует");
        }
        if (currentEdges.stream().anyMatch(edge -> sameEdge(edge, request) && contradicts(edge.getSemanticType(), request.semanticType()))) {
            throw new ApiException(HttpStatus.CONFLICT, "GRAPH_EDGE_CONTRADICTION", "Связь противоречит уже существующей связи между этими объектами");
        }
        Hypothesis hypothesis = hypotheses.save(new Hypothesis(caseFile, request.hypothesisTitle(), request.hypothesisText(), request.confidence(), actor));
        GraphEdge edge = edges.save(new GraphEdge(caseFile, request.source().type(), request.source().id(), request.target().type(), request.target().id(), request.semanticType(), request.confidence(), hypothesis, actor));
        caseFile.advanceGraphRevision();
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

    private void requireNodeInCase(UUID caseId, NodeRef node) {
        boolean exists = switch (node.type()) {
            case CASE -> node.id().equals(caseId);
            case EVIDENCE -> evidence.findById(node.id()).map(item -> item.getCaseFile().getId().equals(caseId)).orElse(false);
            case TASK -> tasks.findById(node.id()).map(item -> item.getCaseFile().getId().equals(caseId)).orElse(false);
            case LAB_REQUEST -> labs.findById(node.id()).map(item -> item.getCaseFile().getId().equals(caseId)).orElse(false);
            case REPORT -> reports.findById(node.id()).map(item -> item.getCaseFile().getId().equals(caseId)).orElse(false);
            case LOCATION -> scenes.findById(node.id()).map(item -> item.getCaseFile().getId().equals(caseId)).orElse(false);
            case HYPOTHESIS -> hypotheses.findById(node.id()).map(item -> item.getCaseFile().getId().equals(caseId)).orElse(false);
            case PERSON -> false;
        };
        if (!exists) throw new ApiException(HttpStatus.BAD_REQUEST, "GRAPH_NODE_INVALID", "Узел не существует или не принадлежит выбранному делу");
    }

    private void requireNodeVersion(NodeRef node) {
        if (node.version() == null) return;
        Long current = switch (node.type()) {
            case CASE -> cases.findById(node.id()).map(CaseFile::getVersion).orElse(null);
            case EVIDENCE -> evidence.findById(node.id()).map(Evidence::getVersion).orElse(null);
            case TASK -> tasks.findById(node.id()).map(TaskItem::getVersion).orElse(null);
            case LAB_REQUEST -> labs.findById(node.id()).map(LabRequest::getVersion).orElse(null);
            default -> 0L;
        };
        if (current == null || !current.equals(node.version())) throw new ApiException(HttpStatus.CONFLICT, "GRAPH_STALE", "Один из выбранных объектов был изменен. Граф обновлен");
    }

    private boolean sameEdge(GraphEdge edge, GraphEdgeRequest request) {
        return edge.getSourceType() == request.source().type() && edge.getSourceId().equals(request.source().id())
                && edge.getTargetType() == request.target().type() && edge.getTargetId().equals(request.target().id());
    }

    private String normalize(String value) { return value.trim().toLowerCase(java.util.Locale.ROOT); }

    private boolean contradicts(String existing, String requested) {
        var positive = java.util.Set.of("подтверждает", "supports", "доказывает");
        var negative = java.util.Set.of("опровергает", "contradicts", "исключает", "противоречит");
        String left = normalize(existing); String right = normalize(requested);
        return positive.contains(left) && negative.contains(right) || negative.contains(left) && positive.contains(right);
    }
}
