package com.example.demo.post;

import java.util.List;

public record PostListResponse(
        List<PostSummaryResponse> items,
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
