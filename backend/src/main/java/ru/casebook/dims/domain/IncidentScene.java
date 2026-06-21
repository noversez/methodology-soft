package ru.casebook.dims.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_scenes")
public class IncidentScene {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private CaseFile caseFile;
    @Column(nullable = false) private String title;
    @Column(nullable = false, length = 4000) private String description;
    @Column(nullable = false) private String address;
    private Double latitude;
    private Double longitude;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private UserAccount createdBy;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    protected IncidentScene() {}
    public IncidentScene(CaseFile caseFile, String title, String description, String address, Double latitude, Double longitude, UserAccount createdBy) {
        this.caseFile = caseFile; this.title = title; this.description = description; this.address = address;
        this.latitude = latitude; this.longitude = longitude; this.createdBy = createdBy;
    }
    public UUID getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
