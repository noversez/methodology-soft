package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cases")
public class CaseFile {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant openedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.NEW;

    @Column(nullable = false, length = 4000)
    private String description;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount createdBy;

    @Version
    private long version;

    @Column(nullable = false)
    private long graphRevision;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected CaseFile() {
    }

    public CaseFile(String registrationNumber, String title, Instant openedAt, Priority priority, String description, UserAccount createdBy) {
        this.registrationNumber = registrationNumber;
        this.title = title;
        this.openedAt = openedAt;
        this.priority = priority;
        this.description = description;
        this.createdBy = createdBy;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getTitle() { return title; }
    public Instant getOpenedAt() { return openedAt; }
    public Priority getPriority() { return priority; }
    public CaseStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public UserAccount getCreatedBy() { return createdBy; }
    public long getVersion() { return version; }
    public long getGraphRevision() { return graphRevision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, Instant openedAt, Priority priority, CaseStatus status, String description) {
        this.title = title;
        this.openedAt = openedAt;
        this.priority = priority;
        this.status = status;
        this.description = description;
    }

    public void advanceGraphRevision() { graphRevision++; }
}
