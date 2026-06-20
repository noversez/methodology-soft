package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hypotheses")
public class Hypothesis {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CaseFile caseFile;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 12000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Confidence confidence;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount author;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Hypothesis() {
    }

    public Hypothesis(CaseFile caseFile, String title, String text, Confidence confidence, UserAccount author) {
        this.caseFile = caseFile;
        this.title = title;
        this.text = text;
        this.confidence = confidence;
        this.author = author;
    }

    public UUID getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getTitle() { return title; }
    public String getText() { return text; }
    public Confidence getConfidence() { return confidence; }
    public UserAccount getAuthor() { return author; }
    public Instant getCreatedAt() { return createdAt; }
}
