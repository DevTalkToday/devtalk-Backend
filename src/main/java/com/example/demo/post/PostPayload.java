package com.example.demo.post;

import java.util.List;

public record PostPayload(
        String title,
        String content,
        String category,
        List<String> tags,
        List<String> majors,
        QuestionPayload question,
        BugPayload bug
) {
    public record QuestionPayload(
            boolean solved,
            String environment,
            String tried,
            String acceptedCommentId
    ) {
    }

    public record BugPayload(
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
}
