package ru.casebook.dims.api;

import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.UserDto;
import ru.casebook.dims.domain.AuditLog;
import ru.casebook.dims.domain.Notification;
import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.repo.AuditLogRepository;
import ru.casebook.dims.repo.NotificationRepository;
import ru.casebook.dims.repo.UserRepository;
import ru.casebook.dims.service.CurrentUserService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final NotificationRepository notifications;
    private final CurrentUserService currentUserService;

    public SystemController(UserRepository users, AuditLogRepository auditLogs, NotificationRepository notifications, CurrentUserService currentUserService) {
        this.users = users;
        this.auditLogs = auditLogs;
        this.notifications = notifications;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/users")
    public List<UserDto> users() {
        return users.findAll().stream().map(UserDto::from).toList();
    }

    @GetMapping("/me")
    public UserDto me(@RequestHeader("X-User-Id") String userId) {
        return UserDto.from(currentUserService.requireUser(userId));
    }

    @GetMapping("/audit-logs")
    public List<AuditLogDto> audit(@RequestHeader("X-User-Id") String userId) {
        UserAccount actor = currentUserService.requireUser(userId);
        currentUserService.requireAnyRole(actor, Role.ADMIN);
        return auditLogs.findAll().stream().map(AuditLogDto::from).toList();
    }

    @GetMapping("/notifications")
    public List<NotificationDto> notifications(@RequestHeader("X-User-Id") String userId, @RequestParam(required = false) UUID caseId) {
        UserAccount actor = currentUserService.requireUser(userId);
        if (caseId != null) {
            return notifications.findByRecipientIdAndPayloadJsonContainingOrderByCreatedAtDesc(actor.getId(), "\"caseId\":\"" + caseId + "\"").stream().map(NotificationDto::from).toList();
        }
        return notifications.findByRecipientIdOrderByCreatedAtDesc(actor.getId()).stream().map(NotificationDto::from).toList();
    }

    public record AuditLogDto(UUID id, UUID actorId, String action, String entityType, UUID entityId, Instant timestamp, String metadataJson) {
        static AuditLogDto from(AuditLog item) {
            return new AuditLogDto(item.getId(), item.getActor().getId(), item.getAction(), item.getEntityType(), item.getEntityId(), item.getTimestamp(), item.getMetadataJson());
        }
    }

    public record NotificationDto(UUID id, UUID recipientId, String type, String payloadJson, Instant readAt, Instant createdAt) {
        static NotificationDto from(Notification item) {
            return new NotificationDto(item.getId(), item.getRecipient().getId(), item.getType(), item.getPayloadJson(), item.getReadAt(), item.getCreatedAt());
        }
    }
}
