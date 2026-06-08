package com.example.demo.post;

import java.time.Instant;
import java.util.List;

public record PostResponse(
        String id,
        String title,
        String excerpt,
        String content,
        String category,
        PostAuthorResponse author,
        Instant createdAt,
        Instant updatedAt,
        int commentCount,
        int likeCount,
        int bookmarkCount,
        boolean bookmarked,
        boolean liked,
        int viewCount,
        List<String> tags,
        List<String> majors,
        List<PostCommentResponse> comments,
        QuestionResponse question,
        BugResponse bug,
        boolean canEdit,
        boolean canDelete,
        boolean canAcceptComments
) {
}
