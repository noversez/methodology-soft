package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.GraphEdge;
import ru.casebook.dims.domain.NodeType;

import java.util.List;
import java.util.UUID;

public interface GraphEdgeRepository extends JpaRepository<GraphEdge, UUID> {
    List<GraphEdge> findByCaseFileId(UUID caseId);
    boolean existsByCaseFileIdAndSourceTypeAndSourceIdAndTargetTypeAndTargetIdAndSemanticType(
            UUID caseId,
            NodeType sourceType,
            UUID sourceId,
            NodeType targetType,
            UUID targetId,
            String semanticType
    );
}
