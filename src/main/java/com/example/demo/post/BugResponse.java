package com.example.demo.post;

import java.util.List;

public record BugResponse(
        String status,
        String priority,
        String assignee,
        String environment,
        String expected,
        String actual,
        List<String> reproductionSteps,
        List<String> labels,
        int watchers,
        String acceptedCommentId
) {
}
