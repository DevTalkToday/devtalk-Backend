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
            String expected,
            String actual,
            List<String> reproductionSteps,
            String acceptedCommentId
    ) {
    }

    public record BugPayload(
            String status,
            String expected,
            String actual,
            List<String> reproductionSteps,
            Integer watchers,
            String acceptedCommentId
    ) {
    }
}
