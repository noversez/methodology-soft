package ru.casebook.dims.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interviews")
public class Interview {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private CaseFile caseFile;
    @Column(nullable = false) private String interviewee;
    @Column(nullable = false) private Instant occurredAt;
    @Column(nullable = false, length = 12000) private String protocolText;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private UserAccount createdBy;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    protected Interview() {}
    public Interview(CaseFile caseFile, String interviewee, Instant occurredAt, String protocolText, UserAccount createdBy) {
        this.caseFile=caseFile; this.interviewee=interviewee; this.occurredAt=occurredAt; this.protocolText=protocolText; this.createdBy=createdBy;
    }
    public UUID getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getInterviewee() { return interviewee; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getProtocolText() { return protocolText; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void update(String interviewee, Instant occurredAt, String protocolText) {
        this.interviewee=interviewee; this.occurredAt=occurredAt; this.protocolText=protocolText;
    }
}
