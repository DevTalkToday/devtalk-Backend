package com.example.demo.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "reports",
        indexes = {
                @Index(name = "idx_reports_created", columnList = "created_at"),
                @Index(name = "idx_reports_target", columnList = "target_type, target_id")
        }
)
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 80)
    private String targetId;

    @Column(name = "target_label", length = 120)
    private String targetLabel;

    @Column(name = "target_url", length = 300)
    private String targetUrl;

    @Column(nullable = false, length = 100)
    private String subject;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "reporter_id")
    private Long reporterId;

    @Column(name = "reporter_label", length = 160)
    private String reporterLabel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Report() {
    }

    public Report(
            String targetType,
            String targetId,
            String targetLabel,
            String targetUrl,
            String subject,
            String content,
            Long reporterId,
            String reporterLabel
    ) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.targetUrl = targetUrl;
        this.subject = subject;
        this.content = content;
        this.reporterId = reporterId;
        this.reporterLabel = reporterLabel;
    }

    public Long getId() {
        return id;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public String getReporterLabel() {
        return reporterLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
