package com.example.demo.notification;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final AuthService authService;

    public NotificationController(NotificationService notificationService, AuthService authService) {
        this.notificationService = notificationService;
        this.authService = authService;
    }

    @GetMapping
    public List<NotificationResponse> list(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return notificationService.list(user, limit);
    }

    @GetMapping("/unread-count")
    public NotificationUnreadCountResponse unreadCount(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return notificationService.unreadCount(user);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return notificationService.markRead(user, id);
    }

    @PatchMapping("/read-all")
    public NotificationReadAllResponse markAllRead(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return notificationService.markAllRead(user);
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
