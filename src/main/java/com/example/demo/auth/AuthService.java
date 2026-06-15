package com.example.demo.auth;

import com.example.demo.auth.dto.AuthResponse;
import com.example.demo.auth.dto.CompleteProfileRequest;
import com.example.demo.auth.dto.EmailVerificationConfirmRequest;
import com.example.demo.auth.dto.EmailVerificationConfirmResponse;
import com.example.demo.auth.dto.EmailVerificationRequest;
import com.example.demo.auth.dto.EmailVerificationRequestResponse;
import com.example.demo.auth.dto.GithubLoginRequest;
import com.example.demo.auth.dto.GoogleLoginRequest;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.SignupRequest;
import com.example.demo.auth.dto.UserResponse;
import com.example.demo.auth.github.GithubOAuthClient;
import com.example.demo.auth.github.GithubUserProfile;
import com.example.demo.auth.google.GoogleOAuthClient;
import com.example.demo.auth.google.GoogleUserProfile;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(2);
    private static final String GUEST_USERNAME = "__guest__";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final GithubOAuthClient githubOAuthClient;
    private final GoogleOAuthClient googleOAuthClient;
    private final EmailVerificationService emailVerificationService;
    private final AuthRateLimitService authRateLimitService;

    public AuthService(
            UserRepository userRepository,
            AuthTokenRepository authTokenRepository,
            OAuthAccountRepository oAuthAccountRepository,
            PasswordEncoder passwordEncoder,
            GithubOAuthClient githubOAuthClient,
            GoogleOAuthClient googleOAuthClient,
            EmailVerificationService emailVerificationService,
            AuthRateLimitService authRateLimitService
    ) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.oAuthAccountRepository = oAuthAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.githubOAuthClient = githubOAuthClient;
        this.googleOAuthClient = googleOAuthClient;
        this.emailVerificationService = emailVerificationService;
        this.authRateLimitService = authRateLimitService;
    }

    public EmailVerificationRequestResponse requestEmailVerification(EmailVerificationRequest request) {
        return emailVerificationService.requestCode(request);
    }

    public EmailVerificationConfirmResponse confirmEmailVerification(EmailVerificationConfirmRequest request) {
        return emailVerificationService.confirmCode(request);
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(email) || userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        emailVerificationService.assertVerified(email);

        AppUser user = new AppUser(
                email,
                normalizeNickname(request.nickname(), email),
                email,
                passwordEncoder.encode(request.password()),
                true,
                normalizeMajors(request.majors())
        );

        AuthResponse response = createAuthResponse(userRepository.save(user));
        emailVerificationService.clearVerification(email);
        return response;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.username().trim();
        authRateLimitService.checkLoginIdentifierAllowed(identifier);
        AppUser user = userRepository.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        authRateLimitService.resetLoginIdentifier(identifier);
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginWithGithub(GithubLoginRequest request) {
        GithubUserProfile profile = githubOAuthClient.fetchProfile(request);

        AppUser user = oAuthAccountRepository.findByProviderAndProviderUserId("github", profile.providerUserId())
                .map(OAuthAccount::getUser)
                .orElseGet(() -> createGithubUser(profile));

        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleUserProfile profile = googleOAuthClient.fetchProfile(request);

        AppUser user = oAuthAccountRepository.findByProviderAndProviderUserId("google", profile.providerUserId())
                .map(OAuthAccount::getUser)
                .orElseGet(() -> createGoogleUser(profile));

        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse issueGuestToken() {
        authTokenRepository.deleteByExpiresAtBefore(Instant.now());

        String token = randomToken();
        Instant expiresAt = Instant.now().plus(ACCESS_TOKEN_TTL);
        authTokenRepository.save(new AuthToken(token, getOrCreateGuestUser(), expiresAt, true));

        return new AuthResponse(token, "Bearer", ACCESS_TOKEN_TTL.toSeconds(), null);
    }

    @Transactional
    public void logout(String token) {
        authTokenRepository.deleteByToken(token);
    }

    @Transactional
    public AuthToken authenticateToken(String token) {
        AuthToken authToken = authTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token"));

        if (authToken.isExpired()) {
            authTokenRepository.deleteByToken(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token expired");
        }

        return authToken;
    }

    @Transactional
    public AppUser authenticate(String token) {
        AuthToken authToken = authenticateToken(token);
        if (authToken.isGuest() || authToken.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required");
        }

        return authToken.getUser();
    }

    private AuthResponse createAuthResponse(AppUser user) {
        authTokenRepository.deleteByExpiresAtBefore(Instant.now());

        String token = randomToken();
        Instant expiresAt = Instant.now().plus(ACCESS_TOKEN_TTL);
        authTokenRepository.save(new AuthToken(token, user, expiresAt));

        return new AuthResponse(token, "Bearer", ACCESS_TOKEN_TTL.toSeconds(), UserResponse.from(user));
    }

    private AppUser getOrCreateGuestUser() {
        return userRepository.findByUsernameIgnoreCase(GUEST_USERNAME)
                .orElseGet(() -> userRepository.save(new AppUser(GUEST_USERNAME, "게스트", null, null, false, List.of())));
    }

    private AppUser createGithubUser(GithubUserProfile profile) {
        if (profile.email() != null && !profile.email().isBlank()) {
            AppUser existingUser = userRepository.findByEmailIgnoreCase(profile.email().trim()).orElse(null);
            if (existingUser != null) {
                oAuthAccountRepository.save(new OAuthAccount("github", profile.providerUserId(), profile.email(), existingUser));
                return existingUser;
            }
        }

        String username = uniqueUsername("github_" + safeUsername(profile.login()));
        String nickname = profile.name() != null && !profile.name().isBlank()
                ? profile.name().trim()
                : profile.login();

        AppUser user = userRepository.save(new AppUser(username, nickname, profile.email(), null, false, List.of()));
        oAuthAccountRepository.save(new OAuthAccount("github", profile.providerUserId(), profile.email(), user));
        return user;
    }

    private AppUser createGoogleUser(GoogleUserProfile profile) {
        if (profile.email() != null && !profile.email().isBlank()) {
            AppUser existingUser = userRepository.findByEmailIgnoreCase(profile.email().trim()).orElse(null);
            if (existingUser != null) {
                oAuthAccountRepository.save(new OAuthAccount("google", profile.providerUserId(), profile.email(), existingUser));
                return existingUser;
            }
        }

        String username = uniqueUsername("google_" + safeUsername(profile.email()));
        String nickname = profile.name() != null && !profile.name().isBlank()
                ? profile.name().trim()
                : username;

        AppUser user = userRepository.save(new AppUser(username, nickname, profile.email(), null, false, List.of()));
        oAuthAccountRepository.save(new OAuthAccount("google", profile.providerUserId(), profile.email(), user));
        return user;
    }

    @Transactional
    public UserResponse completeProfile(String token, CompleteProfileRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password confirmation does not match");
        }

        AppUser user = authenticate(token);
        String nickname = request.nickname().trim();
        String description = request.description() == null || request.description().isBlank()
                ? null
                : request.description().trim();

        user.completeProfile(nickname, passwordEncoder.encode(request.password()), description, normalizeMajors(request.majors()));
        return UserResponse.from(user);
    }

    private String uniqueUsername(String baseUsername) {
        String candidate = baseUsername;
        int suffix = 1;
        while (userRepository.existsByUsernameIgnoreCase(candidate)) {
            candidate = baseUsername + "_" + suffix;
            suffix += 1;
        }
        return candidate;
    }

    private static String safeUsername(String value) {
        String safe = value == null ? "user" : value.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        if (safe.length() < 3) return "user_" + safe;
        if (safe.length() > 60) return safe.substring(0, 60);
        return safe;
    }

    private static String randomToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeNickname(String nickname, String username) {
        if (nickname == null || nickname.isBlank()) return defaultNickname(username);
        return nickname.trim();
    }

    private static String defaultNickname(String username) {
        String normalized = username == null ? "" : username.trim();
        int atIndex = normalized.indexOf('@');
        String localPart = atIndex <= 0 ? normalized : normalized.substring(0, atIndex).trim();
        return localPart.isBlank() ? normalized : localPart;
    }

    private static List<String> normalizeMajors(List<String> majors) {
        if (majors == null) return List.of();
        return majors.stream()
                .filter(major -> major != null && !major.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
