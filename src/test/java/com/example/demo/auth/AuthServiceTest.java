package com.example.demo.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private final AuthService service = new AuthService(
            userRepository,
            authTokenRepository,
            oAuthAccountRepository,
            passwordEncoder,
            mock(GithubOAuthClient.class),
            mock(GoogleOAuthClient.class)
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
}
