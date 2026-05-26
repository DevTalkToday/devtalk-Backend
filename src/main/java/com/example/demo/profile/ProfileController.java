package com.example.demo.profile;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthService;
import com.example.demo.auth.dto.UserResponse;
import com.example.demo.post.PostListResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/profile")
public class ProfileController {
    private final ProfileService profileService;
    private final AuthService authService;

    public ProfileController(ProfileService profileService, AuthService authService) {
        this.profileService = profileService;
        this.authService = authService;
    }

    @GetMapping("/me")
    public ProfileResponse me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return profileService.getMe(user);
    }

    @PatchMapping("/me")
    public UserResponse update(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody ProfileUpdatePayload payload
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return profileService.update(user, payload);
    }

    @PatchMapping("/me/avatar")
    public UserResponse updateAvatar(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody ProfileAvatarPayload payload
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return profileService.updateAvatar(user, payload);
    }

    @GetMapping("/me/posts")
    public PostListResponse posts(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "24") int limit
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return profileService.listPosts(user, page, limit);
    }

    @GetMapping("/me/comments")
    public ProfileCommentListResponse comments(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "24") int limit
    ) {
        AppUser user = authService.authenticate(readBearerToken(authorization));
        return profileService.listComments(user, page, limit);
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
