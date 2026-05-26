package com.example.demo.profile;

import java.util.List;

public record ProfileCommentListResponse(
        List<ProfileCommentResponse> items,
        PageInfo pageInfo
) {
    public record PageInfo(
            int page,
            int limit,
            int totalCount,
            int totalPages,
            boolean hasNextPage,
            boolean hasPreviousPage
    ) {
    }
}
