package com.example.demo.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "email_verifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_verifications_email", columnNames = "email"),
        indexes = @Index(name = "idx_email_verifications_expires_at", columnList = "expires_at")
)
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant verifiedAt;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    protected EmailVerification() {
    }

    public EmailVerification(String email, String codeHash, Instant expiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.requestedAt = Instant.now();
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void renew(String codeHash, Instant expiresAt) {
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.verifiedAt = null;
        this.requestedAt = Instant.now();
    }

    public void markVerified(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
