package com.example.demo.auth;

import com.example.demo.auth.dto.AuthResponse;
import com.example.demo.auth.dto.CompleteProfileRequest;
import com.example.demo.auth.dto.GithubLoginRequest;
import com.example.demo.auth.dto.GoogleLoginRequest;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.MeResponse;
import com.example.demo.auth.dto.SignupRequest;
import com.example.demo.auth.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/token")
    public AuthResponse token() {
        return authService.issueGuestToken();
    }

    @PostMapping("/github")
    public AuthResponse github(@Valid @RequestBody GithubLoginRequest request) {
        return authService.loginWithGithub(request);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }

    @GetMapping("/me")
    public MeResponse me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        AuthToken token = authService.authenticateToken(readBearerToken(authorization));
        AppUser user = token.isGuest() ? null : token.getUser();
        return new MeResponse(user == null ? null : UserResponse.from(user));
    }

    @PostMapping("/profile")
    public UserResponse completeProfile(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody CompleteProfileRequest request
    ) {
        return authService.completeProfile(readBearerToken(authorization), request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        authService.logout(readBearerToken(authorization));
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
