package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachments")
public class Attachment {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String ownerType;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String storagePath;

    @Column(nullable = false)
    private String sha256;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount uploadedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Attachment() {
    }

    public Attachment(String ownerType, UUID ownerId, String fileName, String mimeType, long sizeBytes, String storagePath, String sha256, UserAccount uploadedBy) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
    }

    public UUID getId() { return id; }
    public String getOwnerType() { return ownerType; }
    public UUID getOwnerId() { return ownerId; }
    public String getFileName() { return fileName; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public String getSha256() { return sha256; }
    public UserAccount getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
