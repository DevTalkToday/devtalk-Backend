package com.example.demo.profile;

public record PublicProfileResponse(
        PublicProfileUserResponse user,
        long postCount,
        long commentCount,
        long acceptedCommentCount
) {
}
