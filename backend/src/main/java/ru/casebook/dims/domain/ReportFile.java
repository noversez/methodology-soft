package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class ReportFile {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CaseFile caseFile;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    @Column(nullable = false)
    private String format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private String sha256;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount approvedBy;

    @Column(nullable = false)
    private Instant approvedAt = Instant.now();

    @Column(nullable = false, length = 40000)
    private String content;

    protected ReportFile() {
    }

    public ReportFile(CaseFile caseFile, String registrationNumber, String format, ReportStatus status, String storagePath, String sha256, UserAccount approvedBy, String content) {
        this.caseFile = caseFile;
        this.registrationNumber = registrationNumber;
        this.format = format;
        this.status = status;
        this.storagePath = storagePath;
        this.sha256 = sha256;
        this.approvedBy = approvedBy;
        this.content = content;
    }

    public UUID getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getFormat() { return format; }
    public ReportStatus getStatus() { return status; }
    public String getStoragePath() { return storagePath; }
    public String getSha256() { return sha256; }
    public UserAccount getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getContent() { return content; }
}
