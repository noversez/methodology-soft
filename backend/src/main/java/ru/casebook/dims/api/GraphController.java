package ru.casebook.dims.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.GraphDtos.*;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.service.CurrentUserService;
import ru.casebook.dims.service.GraphService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}")
public class GraphController {
    private final GraphService graphService;
    private final CurrentUserService currentUserService;

    public GraphController(GraphService graphService, CurrentUserService currentUserService) {
        this.graphService = graphService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/graph")
    public GraphResponse graph(@PathVariable UUID caseId) {
        return graphService.graph(caseId);
    }

    @PostMapping("/graph/edges")
    public GraphEdgeResponse createEdge(@RequestHeader("X-User-Id") String userId, @PathVariable UUID caseId, @Valid @RequestBody GraphEdgeRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return GraphEdgeResponse.from(graphService.createEdge(actor, caseId, request));
    }

    @GetMapping("/hypotheses")
    public List<HypothesisResponse> hypotheses(@PathVariable UUID caseId) {
        return graphService.hypotheses(caseId).stream().map(HypothesisResponse::from).toList();
    }

    @PostMapping("/hypotheses")
    public HypothesisResponse createHypothesis(@RequestHeader("X-User-Id") String userId, @PathVariable UUID caseId, @Valid @RequestBody HypothesisRequest request) {
        UserAccount actor = currentUserService.requireUser(userId);
        return HypothesisResponse.from(graphService.createHypothesis(actor, caseId, request));
    }
}
