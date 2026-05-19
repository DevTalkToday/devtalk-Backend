package com.example.demo.post;

public record QuestionResponse(
        boolean solved,
        String environment,
        String tried,
        String acceptedCommentId
) {
}
