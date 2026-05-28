package com.example.demo.settings;

import java.util.List;

public record NotificationPreferenceResponse(
        List<NotificationPreferenceItemResponse> preferences
) {
}
