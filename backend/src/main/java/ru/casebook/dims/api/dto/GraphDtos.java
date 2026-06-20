package ru.casebook.dims.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.casebook.dims.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GraphDtos {
    private GraphDtos() {
    }

    public record NodeRef(@NotNull NodeType type, @NotNull UUID id) {
    }

    public record GraphEdgeRequest(
            @NotNull NodeRef source,
            @NotNull NodeRef target,
            @NotBlank String semanticType,
            @NotNull Confidence confidence,
            @NotBlank String hypothesisTitle,
            @NotBlank String hypothesisText
    ) {
    }

    public record HypothesisRequest(
            @NotBlank String title,
            @NotBlank String text,
            @NotNull Confidence confidence
    ) {
    }

    public record GraphNodeResponse(NodeType type, UUID id, String label, String status) {
    }

    public record HypothesisResponse(UUID id, UUID caseId, String title, String text, Confidence confidence, UUID authorId, Instant createdAt) {
        public static HypothesisResponse from(Hypothesis item) {
            return new HypothesisResponse(item.getId(), item.getCaseFile().getId(), item.getTitle(), item.getText(), item.getConfidence(), item.getAuthor().getId(), item.getCreatedAt());
        }
    }

    public record GraphEdgeResponse(
            UUID id,
            UUID caseId,
            NodeRef source,
            NodeRef target,
            String semanticType,
            Confidence confidence,
            UUID hypothesisId,
            UUID createdBy,
            long version,
            Instant createdAt
    ) {
        public static GraphEdgeResponse from(GraphEdge item) {
            return new GraphEdgeResponse(
                    item.getId(),
                    item.getCaseFile().getId(),
                    new NodeRef(item.getSourceType(), item.getSourceId()),
                    new NodeRef(item.getTargetType(), item.getTargetId()),
                    item.getSemanticType(),
                    item.getConfidence(),
                    item.getHypothesis() == null ? null : item.getHypothesis().getId(),
                    item.getCreatedBy().getId(),
                    item.getVersion(),
                    item.getCreatedAt()
            );
        }
    }

    public record GraphResponse(List<GraphNodeResponse> nodes, List<GraphEdgeResponse> edges, boolean filtered, String warning) {
    }
}
