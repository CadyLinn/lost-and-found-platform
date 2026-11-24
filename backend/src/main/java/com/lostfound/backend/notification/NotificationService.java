package com.lostfound.backend.notification;

import com.lostfound.backend.item.Item;
import com.lostfound.backend.user.User;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    public record NotificationResponse(
            Long id, NotificationType type, Long itemId, String itemTitle, boolean read, Instant createdAt) {

        static NotificationResponse from(Notification n) {
            Item item = n.getItem();
            return new NotificationResponse(
                    n.getId(),
                    n.getType(),
                    item == null ? null : item.getId(),
                    item == null ? null : item.getTitle(),
                    n.getReadAt() != null,
                    n.getCreatedAt());
        }
    }

    public record NotificationList(long unreadCount, List<NotificationResponse> notifications) {
    }

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Called by other services inside their own transaction. */
    public void notify(User recipient, NotificationType type, Item item) {
        notificationRepository.save(new Notification(recipient, type, item));
    }

    @Transactional(readOnly = true)
    public NotificationList list(Long userId) {
        List<NotificationResponse> latest = notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(NotificationResponse::from).toList();
        return new NotificationList(notificationRepository.countByUserIdAndReadAtIsNull(userId), latest);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.belongsTo(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.markRead();
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }
}
