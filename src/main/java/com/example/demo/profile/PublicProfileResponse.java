package com.example.demo.profile;

public record PublicProfileResponse(
        PublicProfileUserResponse user,
        long postCount,
        long commentCount,
        long acceptedCommentCount,
        long followerCount,
        long followingCount,
        String viewerFriendshipStatus,
        Long viewerFriendshipId,
        boolean viewerFollowing
) {
}
