package com.example.demo.message;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/messages")
public class MessageController {
    private final MessageService messageService;
    private final AuthService authService;

    public MessageController(MessageService messageService, AuthService authService) {
        this.messageService = messageService;
        this.authService = authService;
    }

    @GetMapping("/conversations")
    public List<ConversationResponse> listConversations(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return messageService.listConversations(user);
    }

    @GetMapping("/unread-count")
    public MessageUnreadCountResponse unreadCount(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return messageService.unreadCount(user);
    }

    @GetMapping("/conversations/{userId}")
    public List<MessageResponse> getConversation(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long userId,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return messageService.getConversation(user, userId, limit);
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody MessagePayload payload
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendMessage(user, payload));
    }

    @PatchMapping("/conversations/{userId}/read")
    public MessageReadResponse markConversationRead(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long userId
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return messageService.markConversationRead(user, userId);
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
