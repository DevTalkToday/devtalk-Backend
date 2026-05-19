package com.example.demo.post;

import java.time.Instant;

public record PostCommentResponse(
        String id,
        PostAuthorResponse author,
        String body,
        Instant createdAt,
        Instant updatedAt,
        int likeCount,
        boolean isAccepted
) {
}
