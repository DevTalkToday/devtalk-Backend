package com.example.demo.settings;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/settings")
public class SettingsController {
    private final SettingsService settingsService;
    private final AuthService authService;

    public SettingsController(SettingsService settingsService, AuthService authService) {
        this.settingsService = settingsService;
        this.authService = authService;
    }

    @PatchMapping("/password")
    public PasswordChangeResponse changePassword(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody PasswordChangePayload payload
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return settingsService.changePassword(user, payload);
    }

    @GetMapping("/notifications")
    public NotificationPreferenceResponse notificationPreferences(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return settingsService.getNotificationPreferences(user);
    }

    @PatchMapping("/notifications")
    public NotificationPreferenceResponse updateNotificationPreferences(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody NotificationPreferencePayload payload
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return settingsService.updateNotificationPreferences(user, payload);
    }

    private static String readBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        return token;
    }
}
