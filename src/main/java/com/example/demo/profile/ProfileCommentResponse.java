package com.example.demo.profile;

import java.time.Instant;

public record ProfileCommentResponse(
        String id,
        String postId,
        String postTitle,
        String postCategory,
        String targetUrl,
        String body,
        Instant createdAt,
        Instant updatedAt,
        int likeCount,
        boolean accepted
) {
}
