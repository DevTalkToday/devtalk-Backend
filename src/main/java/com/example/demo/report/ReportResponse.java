package com.example.demo.report;

import java.time.Instant;

public record ReportResponse(
        Long id,
        String targetType,
        String targetId,
        String targetLabel,
        String targetUrl,
        String subject,
        String content,
        Long reporterId,
        String reporterLabel,
        Instant createdAt
) {
    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getTargetLabel(),
                report.getTargetUrl(),
                report.getSubject(),
                report.getContent(),
                report.getReporterId(),
                report.getReporterLabel(),
                report.getCreatedAt()
        );
    }
}
