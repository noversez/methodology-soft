package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.Notification;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<Notification> findByRecipientIdAndPayloadJsonContainingOrderByCreatedAtDesc(UUID recipientId, String fragment);
    long deleteByRecipientIdAndTypeAndPayloadJsonContaining(UUID recipientId,String type,String fragment);
}
