package ru.casebook.dims.service;

import org.springframework.stereotype.Service;
import ru.casebook.dims.domain.Notification;
import ru.casebook.dims.domain.UserAccount;
import ru.casebook.dims.repo.NotificationRepository;

@Service
public class NotificationService {
    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public void notify(UserAccount recipient, String type, String payloadJson) {
        notifications.save(new Notification(recipient, type, payloadJson));
    }
}
