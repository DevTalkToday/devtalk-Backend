package com.example.demo.friend;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/friends")
public class FriendController {
    private final FriendService friendService;
    private final AuthService authService;

    public FriendController(FriendService friendService, AuthService authService) {
        this.friendService = friendService;
        this.authService = authService;
    }

    @GetMapping
    public FriendSummaryResponse summary(@RequestHeader(name = "Authorization", required = false) String authorization) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return friendService.getSummary(user);
    }

    @GetMapping("/received-count")
    public FriendReceivedCountResponse receivedCount(
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return friendService.receivedCount(user);
    }

    @GetMapping("/search")
    public List<FriendSearchResponse> search(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "q", defaultValue = "") String q
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return friendService.search(user, q);
    }

    @PostMapping("/requests")
    public ResponseEntity<FriendshipResponse> request(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody FriendRequestPayload payload
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return ResponseEntity.status(HttpStatus.CREATED).body(friendService.request(user, payload));
    }

    @PatchMapping("/requests/{id}/accept")
    public FriendshipResponse accept(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return friendService.accept(user, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        friendService.delete(user, id);
        return ResponseEntity.noContent().build();
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
