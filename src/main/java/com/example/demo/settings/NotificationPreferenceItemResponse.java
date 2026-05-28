package com.example.demo.settings;

import com.example.demo.notification.NotificationType;

public record NotificationPreferenceItemResponse(
        NotificationType type,
        boolean enabled
) {
}
