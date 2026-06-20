package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class TaskItem {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CaseFile caseFile;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount assignee;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Column(nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.ASSIGNED;

    @Column(length = 8000)
    private String resultText;

    private UUID resultEvidenceId;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected TaskItem() {
    }

    public TaskItem(CaseFile caseFile, String title, String description, UserAccount assignee, UserAccount createdBy, Priority priority, Instant deadline) {
        this.caseFile = caseFile;
        this.title = title;
        this.description = description;
        this.assignee = assignee;
        this.createdBy = createdBy;
        this.priority = priority;
        this.deadline = deadline;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public UserAccount getAssignee() { return assignee; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Priority getPriority() { return priority; }
    public Instant getDeadline() { return deadline; }
    public TaskStatus getStatus() { return status; }
    public String getResultText() { return resultText; }
    public UUID getResultEvidenceId() { return resultEvidenceId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateStatus(TaskStatus status, String resultText) {
        this.status = status;
        this.resultText = resultText;
    }

    public void updateDetails(String title, String description, UserAccount assignee, Priority priority, Instant deadline) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.deadline = deadline;
        if (!this.assignee.getId().equals(assignee.getId())) {
            this.assignee = assignee;
            this.status = TaskStatus.ASSIGNED;
            this.resultText = null;
            this.resultEvidenceId = null;
        }
    }

    public void reassign(UserAccount assignee) {
        this.assignee = assignee;
        this.status = TaskStatus.ASSIGNED;
    }

    public void linkResultEvidence(UUID evidenceId) {
        this.resultEvidenceId = evidenceId;
    }
}
