package com.example.demo.profile;

import com.example.demo.auth.dto.UserResponse;

public record ProfileResponse(
        UserResponse user,
        long postCount,
        long commentCount,
        long acceptedCommentCount
) {
}
