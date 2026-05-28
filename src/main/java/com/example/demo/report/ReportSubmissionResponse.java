package com.example.demo.report;

import java.time.Instant;

public record ReportSubmissionResponse(
        Long id,
        String status,
        Instant createdAt
) {
}
