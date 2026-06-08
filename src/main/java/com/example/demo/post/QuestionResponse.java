package com.example.demo.post;

import java.util.List;

public record QuestionResponse(
        boolean solved,
        String expected,
        String actual,
        List<String> reproductionSteps,
        String acceptedCommentId
) {
}
