package com.example.demo.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String preview,
        String body,
        String actorName,
        String targetType,
        String targetId,
        String targetUrl,
        Instant createdAt,
        Instant readAt,
        boolean unread
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getPreview(),
                notification.getBody(),
                notification.getActor() == null ? "DevTalk" : notification.getActor().getNickname(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getTargetUrl(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getReadAt() == null
        );
    }
}
