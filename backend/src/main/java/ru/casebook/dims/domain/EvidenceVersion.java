package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence_versions")
public class EvidenceVersion {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Evidence evidence;

    @Column(nullable = false, length = 8000)
    private String descriptionSnapshot;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount changedBy;

    @Column(nullable = false)
    private Instant changedAt = Instant.now();

    @Column(nullable = false)
    private long versionNumber;

    protected EvidenceVersion() {
    }

    public EvidenceVersion(Evidence evidence, String descriptionSnapshot, UserAccount changedBy, long versionNumber) {
        this.evidence = evidence;
        this.descriptionSnapshot = descriptionSnapshot;
        this.changedBy = changedBy;
        this.versionNumber = versionNumber;
    }

    public UUID getId() { return id; }
    public Evidence getEvidence() { return evidence; }
    public String getDescriptionSnapshot() { return descriptionSnapshot; }
    public UserAccount getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public long getVersionNumber() { return versionNumber; }
}
