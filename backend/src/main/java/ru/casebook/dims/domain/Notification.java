package ru.casebook.dims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private UserAccount recipient;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 4000)
    private String payloadJson;

    private Instant readAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    public Notification(UserAccount recipient, String type, String payloadJson) {
        this.recipient = recipient;
        this.type = type;
        this.payloadJson = payloadJson;
    }

    public UUID getId() { return id; }
    public UserAccount getRecipient() { return recipient; }
    public String getType() { return type; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
