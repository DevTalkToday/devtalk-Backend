package com.example.demo.post;

import java.util.List;

public record BugResponse(
        String status,
        String expected,
        String actual,
        List<String> reproductionSteps,
        int watchers,
        String acceptedCommentId
) {
}
