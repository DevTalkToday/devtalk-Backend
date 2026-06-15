package com.example.demo.auth;
import com.example.demo.auth.dto.EmailVerificationConfirmRequest;
import com.example.demo.auth.dto.EmailVerificationConfirmResponse;
import com.example.demo.auth.dto.EmailVerificationRequest;
import com.example.demo.auth.dto.EmailVerificationRequestResponse;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationService {
    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_CONFIRM_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String SEND_FAILURE_REASON = "Failed to send verification email";
    private static final String REQUEST_THROTTLED_REASON = "Email verification request is too frequent";

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean exposeDebugCode;
    private final JavaMailSender mailSender;
    private final String verificationFromAddress;

    @Autowired
    public EmailVerificationService(
            EmailVerificationRepository emailVerificationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.email-verification.expose-debug-code:false}") boolean exposeDebugCode,
            @Value("${app.cors.allowed-origins:${CORS_ALLOWED_ORIGIN:http://localhost:3000}}") String configuredAllowedOrigins,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.auth.email-verification.from:}") String verificationFromAddress
    ) {
        this(
                emailVerificationRepository,
                userRepository,
                passwordEncoder,
                exposeDebugCode,
                configuredAllowedOrigins,
                mailSenderProvider.getIfAvailable(),
                verificationFromAddress
        );
    }

    EmailVerificationService(
            EmailVerificationRepository emailVerificationRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            boolean exposeDebugCode,
            String configuredAllowedOrigins,
            JavaMailSender mailSender,
            String verificationFromAddress
    ) {
        this.emailVerificationRepository = emailVerificationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.exposeDebugCode = exposeDebugCode;
        this.mailSender = mailSender;
        this.verificationFromAddress = verificationFromAddress == null ? "" : verificationFromAddress.trim();
    }

    @Transactional
    public EmailVerificationRequestResponse requestCode(EmailVerificationRequest request) {
        String email = normalizeEmail(request.email());
        rejectIfRegistered(email);
        Instant now = Instant.now();
        emailVerificationRepository.deleteByExpiresAtBefore(now);

        EmailVerification existingVerification = emailVerificationRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existingVerification != null
                && !existingVerification.isExpired(now)
                && existingVerification.wasRequestedRecently(now, REQUEST_COOLDOWN)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, REQUEST_THROTTLED_REASON);
        }

        String code = randomCode();
        Instant expiresAt = now.plus(CODE_TTL);
        String codeHash = passwordEncoder.encode(code);

        EmailVerification verification;
        if (existingVerification != null) {
            existingVerification.renew(codeHash, expiresAt);
            verification = existingVerification;
        } else {
            verification = new EmailVerification(email, codeHash, expiresAt);
        }

        emailVerificationRepository.save(verification);

        boolean emailDelivered = sendVerificationEmail(email, code);
        boolean shouldExposeDebugCode = exposeDebugCode;

        if (!emailDelivered && !shouldExposeDebugCode) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, SEND_FAILURE_REASON);
        }

        if (!emailDelivered) {
            logger.info("Email verification code for {}: {}", email, code);
        }

        return new EmailVerificationRequestResponse(
                email,
                CODE_TTL.toSeconds(),
                emailDelivered,
                shouldExposeDebugCode ? code : null
        );
    }

    @Transactional
    public EmailVerificationConfirmResponse confirmCode(EmailVerificationConfirmRequest request) {
        String email = normalizeEmail(request.email());
        EmailVerification verification = emailVerificationRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email verification code is invalid"));

        Instant now = Instant.now();
        if (verification.isExpired(now)) {
            emailVerificationRepository.delete(verification);
            throw expiredVerificationCode();
        }

        if (verification.getFailedAttempts() >= MAX_CONFIRM_ATTEMPTS) {
            emailVerificationRepository.delete(verification);
            throw expiredVerificationCode();
        }

        if (!passwordEncoder.matches(request.code().trim(), verification.getCodeHash())) {
            verification.markFailedAttempt();
            if (verification.getFailedAttempts() >= MAX_CONFIRM_ATTEMPTS) {
                emailVerificationRepository.delete(verification);
                throw expiredVerificationCode();
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email verification code is invalid");
        }

        verification.markVerified(now);
        return new EmailVerificationConfirmResponse(email, true);
    }

    @Transactional
    public void assertVerified(String email) {
        EmailVerification verification = emailVerificationRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email verification is required"));

        Instant now = Instant.now();
        if (verification.isExpired(now)) {
            emailVerificationRepository.delete(verification);
            throw expiredVerificationCode();
        }

        if (!verification.isVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email verification is required");
        }
    }

    @Transactional
    public void clearVerification(String email) {
        emailVerificationRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .ifPresent(emailVerificationRepository::delete);
    }

    private void rejectIfRegistered(String email) {
        if (userRepository.existsByUsernameIgnoreCase(email) || userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim();
    }

    private boolean sendVerificationEmail(String email, String code) {
        if (mailSender == null) {
            if (!exposeDebugCode) {
                logger.warn("Email sender is not configured for {}", email);
            }
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            if (!verificationFromAddress.isBlank()) {
                message.setFrom(verificationFromAddress);
            }
            message.setSubject("Devtalk 이메일 인증번호");
            message.setText(buildVerificationMailBody(code));
            mailSender.send(message);
            logger.info("Sent email verification code to {}", email);
            return true;
        } catch (MailException exception) {
            logger.warn("Failed to send email verification code to {}", email, exception);
            return false;
        }
    }

    private static ResponseStatusException expiredVerificationCode() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email verification code expired");
    }

    private static String buildVerificationMailBody(String code) {
        return """
                Devtalk 회원가입 이메일 인증번호입니다.

                인증번호: %s

                인증번호는 10분 동안 유효합니다.
                """.formatted(code);
    }

    private static String randomCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}
