package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "graph_edges")
public class GraphEdge {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CaseFile caseFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType sourceType;

    @Column(nullable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType targetType;

    @Column(nullable = false)
    private UUID targetId;

    @Column(nullable = false)
    private String semanticType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Confidence confidence;

    @ManyToOne(fetch = FetchType.LAZY)
    private Hypothesis hypothesis;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount createdBy;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected GraphEdge() {
    }

    public GraphEdge(CaseFile caseFile, NodeType sourceType, UUID sourceId, NodeType targetType, UUID targetId, String semanticType, Confidence confidence, Hypothesis hypothesis, UserAccount createdBy) {
        this.caseFile = caseFile;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.semanticType = semanticType;
        this.confidence = confidence;
        this.hypothesis = hypothesis;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public NodeType getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public NodeType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getSemanticType() { return semanticType; }
    public Confidence getConfidence() { return confidence; }
    public Hypothesis getHypothesis() { return hypothesis; }
    public UserAccount getCreatedBy() { return createdBy; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
