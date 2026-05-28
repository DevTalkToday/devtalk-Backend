package com.example.demo.settings;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.UserRepository;
import com.example.demo.notification.NotificationType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SettingsService {
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 100;
    private static final List<NotificationType> CONFIGURABLE_NOTIFICATION_TYPES = Arrays.stream(NotificationType.values())
            .filter(type -> type != NotificationType.ADMIN_NOTICE)
            .toList();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public SettingsService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            NotificationPreferenceRepository notificationPreferenceRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    @Transactional
    public PasswordChangeResponse changePassword(AppUser currentUser, PasswordChangePayload payload) {
        AppUser user = findUser(currentUser);
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PASSWORD_CHANGE_UNAVAILABLE");
        }

        String currentPassword = payload == null ? null : payload.currentPassword();
        String newPassword = payload == null ? null : payload.newPassword();
        String newPasswordConfirm = payload == null ? null : payload.newPasswordConfirm();

        if (isBlank(currentPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_REQUIRED");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INCORRECT");
        }
        if (!isValidPassword(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NEW_PASSWORD_INVALID");
        }
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NEW_PASSWORD_MISMATCH");
        }

        user.updatePasswordHash(passwordEncoder.encode(newPassword));
        return new PasswordChangeResponse(Instant.now());
    }

    @Transactional
    public NotificationPreferenceResponse getNotificationPreferences(AppUser currentUser) {
        return toNotificationPreferenceResponse(findUser(currentUser));
    }

    @Transactional
    public NotificationPreferenceResponse updateNotificationPreferences(
            AppUser currentUser,
            NotificationPreferencePayload payload
    ) {
        AppUser user = findUser(currentUser);
        Map<NotificationType, Boolean> preferences = payload == null || payload.preferences() == null
                ? Map.of()
                : payload.preferences();

        for (Map.Entry<NotificationType, Boolean> entry : preferences.entrySet()) {
            NotificationType type = entry.getKey();
            Boolean enabled = entry.getValue();
            if (!isConfigurable(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NOTIFICATION_TYPE_NOT_CONFIGURABLE");
            }
            if (enabled == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NOTIFICATION_SETTING_REQUIRED");
            }

            NotificationPreference preference = notificationPreferenceRepository.findByUserAndType(user, type)
                    .orElseGet(() -> notificationPreferenceRepository.save(new NotificationPreference(user, type, true)));
            preference.setEnabled(enabled);
        }

        return toNotificationPreferenceResponse(user);
    }

    private NotificationPreferenceResponse toNotificationPreferenceResponse(AppUser user) {
        Map<NotificationType, Boolean> savedPreferences = notificationPreferenceRepository.findByUser(user)
                .stream()
                .collect(Collectors.toMap(NotificationPreference::getType, NotificationPreference::isEnabled));

        List<NotificationPreferenceItemResponse> preferences = CONFIGURABLE_NOTIFICATION_TYPES.stream()
                .map(type -> new NotificationPreferenceItemResponse(type, savedPreferences.getOrDefault(type, true)))
                .toList();

        return new NotificationPreferenceResponse(preferences);
    }

    private AppUser findUser(AppUser currentUser) {
        return userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required"));
    }

    private static boolean isConfigurable(NotificationType type) {
        return type != null && CONFIGURABLE_NOTIFICATION_TYPES.contains(type);
    }

    private static boolean isValidPassword(String value) {
        return !isBlank(value) && value.length() >= MIN_PASSWORD_LENGTH && value.length() <= MAX_PASSWORD_LENGTH;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
