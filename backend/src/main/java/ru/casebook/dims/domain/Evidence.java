package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence")
public class Evidence {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CaseFile caseFile;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority importance;

    @Column(nullable = false, length = 8000)
    private String description;

    @Column(nullable = false)
    private Instant discoveryDateTime;

    private Double latitude;
    private Double longitude;
    private String locationTitle;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount responsibleUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceStatus status = EvidenceStatus.REGISTERED;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected Evidence() {
    }

    public Evidence(CaseFile caseFile, String registrationNumber, String name, String type, Priority importance, String description, Instant discoveryDateTime, Double latitude, Double longitude, String locationTitle, UserAccount responsibleUser) {
        this.caseFile = caseFile;
        this.registrationNumber = registrationNumber;
        this.name = name;
        this.type = type;
        this.importance = importance;
        this.description = description;
        this.discoveryDateTime = discoveryDateTime;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationTitle = locationTitle;
        this.responsibleUser = responsibleUser;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public CaseFile getCaseFile() { return caseFile; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getName() { return name; }
    public String getType() { return type; }
    public Priority getImportance() { return importance; }
    public String getDescription() { return description; }
    public Instant getDiscoveryDateTime() { return discoveryDateTime; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getLocationTitle() { return locationTitle; }
    public UserAccount getResponsibleUser() { return responsibleUser; }
    public EvidenceStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String type, Priority importance, String description, Instant discoveryDateTime, Double latitude, Double longitude, String locationTitle) {
        this.name = name;
        this.type = type;
        this.importance = importance;
        this.description = description;
        this.discoveryDateTime = discoveryDateTime;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationTitle = locationTitle;
    }

    public void setStatus(EvidenceStatus status) {
        this.status = status;
    }
}
