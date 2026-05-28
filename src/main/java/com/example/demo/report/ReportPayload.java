package com.example.demo.report;

public record ReportPayload(
        String targetType,
        String targetId,
        String targetLabel,
        String targetUrl,
        String subject,
        String content
) {
}
