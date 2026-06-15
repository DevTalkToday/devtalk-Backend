package com.example.demo.auth;

import static com.example.demo.TestSupport.withId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.dto.AuthResponse;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.SignupRequest;
import com.example.demo.auth.github.GithubOAuthClient;
import com.example.demo.auth.google.GoogleOAuthClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthTokenRepository authTokenRepository = mock(AuthTokenRepository.class);
    private final OAuthAccountRepository oAuthAccountRepository = mock(OAuthAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final AuthRateLimitService authRateLimitService = new AuthRateLimitService();
    private final AuthService service = new AuthService(
            userRepository,
            authTokenRepository,
            oAuthAccountRepository,
            passwordEncoder,
            mock(GithubOAuthClient.class),
            mock(GoogleOAuthClient.class),
            emailVerificationService,
            authRateLimitService
    );

    @Test
    void signupRejectsDuplicateEmailBeforeSaving() {
        when(userRepository.existsByUsernameIgnoreCase("taken@example.com")).thenReturn(true);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.signup(new SignupRequest(" taken@example.com ", "password123", "Taken", List.of("backend")))
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("Email already exists", error.getReason());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(AppUser.class));
    }

    @Test
    void loginRejectsWrongPassword() {
        AppUser user = new AppUser("user@example.com", "User", "user@example.com", "encoded", true, List.of());
        when(userRepository.findByUsernameIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.login(new LoginRequest(" user@example.com ", "wrong"))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        assertEquals("Invalid username or password", error.getReason());
    }

    @Test
    void loginFallsBackToEmailForSocialUsers() {
        AppUser user = new AppUser("google_user_example_com", "User", "user@example.com", "encoded", true, List.of());
        when(userRepository.findByUsernameIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);

        AuthResponse response = service.login(new LoginRequest(" user@example.com ", "password123"));

        assertTrue(response.accessToken() != null && !response.accessToken().isBlank());
        assertEquals("user@example.com", response.user().email());
    }

    @Test
    void loginThrottlesRepeatedFailuresForSameIdentifier() {
        when(userRepository.findByUsernameIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        for (int attempt = 0; attempt < 5; attempt += 1) {
            ResponseStatusException error = assertThrows(
                    ResponseStatusException.class,
                    () -> service.login(new LoginRequest("user@example.com", "wrong"))
            );
            assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
            assertEquals("Invalid username or password", error.getReason());
        }

        ResponseStatusException throttled = assertThrows(
                ResponseStatusException.class,
                () -> service.login(new LoginRequest("user@example.com", "wrong"))
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, throttled.getStatusCode());
        assertEquals("Too many login attempts", throttled.getReason());
    }

    @Test
    void signupRequiresVerifiedEmail() {
        when(userRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email verification is required"))
                .when(emailVerificationService)
                .assertVerified("user@example.com");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.signup(new SignupRequest(" user@example.com ", "password123", "User", List.of("backend")))
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Email verification is required", error.getReason());
    }

    @Test
    void signupUsesEmailLocalPartWhenNicknameIsBlank() {
        when(userRepository.existsByUsernameIgnoreCase("hello.world@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("hello.world@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        AuthResponse response = service.signup(
                new SignupRequest(" hello.world@example.com ", "password123", "   ", List.of("backend"))
        );

        assertEquals("hello.world", response.user().nickname());
        verify(emailVerificationService).clearVerification("hello.world@example.com");
    }
}
