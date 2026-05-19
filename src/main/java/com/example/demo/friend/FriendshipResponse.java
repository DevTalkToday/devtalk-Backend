package com.example.demo.friend;

import com.example.demo.auth.AppUser;
import java.time.Instant;

public record FriendshipResponse(
        Long id,
        FriendshipStatus status,
        FriendUserResponse user,
        Instant createdAt,
        Instant respondedAt
) {
    public static FriendshipResponse from(Friendship friendship, AppUser currentUser) {
        AppUser peer = friendship.getRequester().getId().equals(currentUser.getId())
                ? friendship.getAddressee()
                : friendship.getRequester();

        return new FriendshipResponse(
                friendship.getId(),
                friendship.getStatus(),
                FriendUserResponse.from(peer),
                friendship.getCreatedAt(),
                friendship.getRespondedAt()
        );
    }
}
