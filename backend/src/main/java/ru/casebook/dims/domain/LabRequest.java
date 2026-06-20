package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lab_requests")
public class LabRequest {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CaseFile caseFile;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Evidence evidence;

    @Column(nullable = false)
    private String profile;

    @Column(nullable = false, length = 8000)
    private String questions;

    @Column(nullable = false)
    private Instant desiredDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LabRequestStatus status = LabRequestStatus.CREATED;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount requester;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserAccount labAssignee;

    @Column(length = 20000)
    private String resultText;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected LabRequest() {
    }

    public LabRequest(CaseFile caseFile, Evidence evidence, String profile, String questions, Instant desiredDueDate, UserAccount requester, UserAccount labAssignee) {
        this.caseFile = caseFile;
        this.evidence = evidence;
        this.profile = profile;
        this.questions = questions;
        this.desiredDueDate = desiredDueDate;
        this.requester = requester;
        this.labAssignee = labAssignee;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public Evidence getEvidence() { return evidence; }
    public String getProfile() { return profile; }
    public String getQuestions() { return questions; }
    public Instant getDesiredDueDate() { return desiredDueDate; }
    public LabRequestStatus getStatus() { return status; }
    public UserAccount getRequester() { return requester; }
    public UserAccount getLabAssignee() { return labAssignee; }
    public String getResultText() { return resultText; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(LabRequestStatus status) {
        this.status = status;
    }

    public void complete(String resultText) {
        this.status = LabRequestStatus.COMPLETED;
        this.resultText = resultText;
    }
}
