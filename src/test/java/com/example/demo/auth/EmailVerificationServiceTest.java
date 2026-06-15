package com.example.demo.auth;

import static com.example.demo.TestSupport.setField;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.auth.dto.EmailVerificationConfirmRequest;
import com.example.demo.auth.dto.EmailVerificationConfirmResponse;
import com.example.demo.auth.dto.EmailVerificationRequest;
import com.example.demo.auth.dto.EmailVerificationRequestResponse;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class EmailVerificationServiceTest {
    private final EmailVerificationRepository emailVerificationRepository = mock(EmailVerificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void requestCodeReturnsDebugCodeWhenEnabled() {
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                true,
                "http://localhost:3000",
                (JavaMailSender) null,
                ""
        );

        when(userRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        EmailVerificationRequestResponse response = service.requestCode(new EmailVerificationRequest("user@example.com"));

        assertEquals("user@example.com", response.email());
        assertEquals(600, response.expiresInSeconds());
        assertEquals(false, response.emailSent());
        assertNotNull(response.debugCode());
        assertEquals(6, response.debugCode().length());
        verify(emailVerificationRepository).save(any(EmailVerification.class));
    }

    @Test
    void confirmCodeMarksVerificationAsVerified() {
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                false,
                "http://localhost:3000",
                (JavaMailSender) null,
                ""
        );
        EmailVerification verification = new EmailVerification("user@example.com", "encoded", Instant.now().plusSeconds(600));

        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("123456", "encoded")).thenReturn(true);

        EmailVerificationConfirmResponse response = service.confirmCode(
                new EmailVerificationConfirmRequest("user@example.com", "123456")
        );

        assertEquals("user@example.com", response.email());
        assertEquals(true, response.verified());
        assertNotNull(verification.getVerifiedAt());
    }

    @Test
    void requestCodeRejectsRapidResend() {
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                false,
                "http://localhost:3000",
                (JavaMailSender) null,
                ""
        );
        EmailVerification verification = new EmailVerification("user@example.com", "encoded", Instant.now().plusSeconds(600));

        when(userRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(verification));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestCode(new EmailVerificationRequest("user@example.com"))
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertEquals("Email verification request is too frequent", error.getReason());
        verify(emailVerificationRepository, never()).save(any(EmailVerification.class));
    }

    @Test
    void confirmCodeExpiresAfterTooManyFailedAttempts() {
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                false,
                "http://localhost:3000",
                (JavaMailSender) null,
                ""
        );
        EmailVerification verification = new EmailVerification("user@example.com", "encoded", Instant.now().plusSeconds(600));
        setField(verification, "failedAttempts", 4);

        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("123456", "encoded")).thenReturn(false);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.confirmCode(new EmailVerificationConfirmRequest("user@example.com", "123456"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Email verification code expired", error.getReason());
        verify(emailVerificationRepository).delete(verification);
    }

    @Test
    void assertVerifiedRejectsPendingVerification() {
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                false,
                "http://localhost:3000",
                (JavaMailSender) null,
                ""
        );
        EmailVerification verification = new EmailVerification("user@example.com", "encoded", Instant.now().plusSeconds(600));

        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(verification));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.assertVerified("user@example.com")
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Email verification is required", error.getReason());
        assertNull(verification.getVerifiedAt());
    }

    @Test
    void requestCodeReturnsDebugCodeWhenMailSenderIsMissingOnLocalhost() {
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                false,
                "http://localhost:3000",
                (JavaMailSender) null,
                ""
        );

        when(userRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestCode(new EmailVerificationRequest("user@example.com"))
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatusCode());
        assertEquals("Failed to send verification email", error.getReason());
    }

    @Test
    void requestCodeReturnsDebugCodeWhenMultipleOriginsIncludeLocalhost() {
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                false,
                "https://devtalk.kr,http://localhost:3000,https://*.vercel.app",
                (JavaMailSender) null,
                ""
        );

        when(userRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestCode(new EmailVerificationRequest("user@example.com"))
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatusCode());
        assertEquals("Failed to send verification email", error.getReason());
    }

    @Test
    void requestCodeFailsWhenMailSenderIsMissingOutsideLocalhost() {
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                false,
                "https://devtalk.example.com",
                (JavaMailSender) null,
                ""
        );

        when(userRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestCode(new EmailVerificationRequest("user@example.com"))
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatusCode());
        assertEquals("Failed to send verification email", error.getReason());
    }

    @Test
    void requestCodeSendsEmailWhenMailSenderIsConfigured() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                false,
                "https://devtalk.example.com",
                mailSender,
                "noreply@devtalk.local"
        );

        when(userRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        EmailVerificationRequestResponse response = service.requestCode(new EmailVerificationRequest("user@example.com"));

        assertEquals(true, response.emailSent());
        assertNull(response.debugCode());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestCodeFallsBackToDebugCodeWhenMailSendFailsOnLocalhost() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                false,
                "http://localhost:3000",
                mailSender,
                "noreply@devtalk.local"
        );

        when(userRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        org.mockito.Mockito.doThrow(new MailSendException("boom")).when(mailSender).send(any(SimpleMailMessage.class));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestCode(new EmailVerificationRequest("user@example.com"))
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatusCode());
        assertEquals("Failed to send verification email", error.getReason());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestCodeReturnsDebugCodeWhenMailSendFailsAndDebugIsEnabled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                true,
                "https://devtalk.example.com",
                mailSender,
                "noreply@devtalk.local"
        );

        when(userRepository.existsByUsernameIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(emailVerificationRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        org.mockito.Mockito.doThrow(new MailSendException("boom")).when(mailSender).send(any(SimpleMailMessage.class));

        EmailVerificationRequestResponse response = service.requestCode(new EmailVerificationRequest("user@example.com"));

        assertEquals(false, response.emailSent());
        assertNotNull(response.debugCode());
        assertEquals(6, response.debugCode().length());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
