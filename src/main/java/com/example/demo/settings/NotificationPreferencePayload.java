package com.example.demo.settings;

import com.example.demo.notification.NotificationType;
import java.util.Map;

public record NotificationPreferencePayload(
        Map<NotificationType, Boolean> preferences
) {
}
