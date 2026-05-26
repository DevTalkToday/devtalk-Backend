package com.example.demo.notification;

import com.example.demo.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_recipient_created", columnList = "recipient_id, created_at"),
                @Index(name = "idx_notifications_recipient_read", columnList = "recipient_id, read_at")
        }
)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 240)
    private String preview;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(length = 40)
    private String targetType;

    @Column(length = 80)
    private String targetId;

    @Column(length = 240)
    private String targetUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(
            AppUser recipient,
            AppUser actor,
            NotificationType type,
            String title,
            String preview,
            String body,
            String targetType,
            String targetId,
            String targetUrl
    ) {
        this.recipient = recipient;
        this.actor = actor;
        this.type = type;
        this.title = title;
        this.preview = preview;
        this.body = body;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetUrl = targetUrl;
    }

    public Long getId() {
        return id;
    }

    public AppUser getRecipient() {
        return recipient;
    }

    public AppUser getActor() {
        return actor;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getPreview() {
        return preview;
    }

    public String getBody() {
        return body;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }
}
