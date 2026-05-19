package com.example.demo.friend;

public record FriendSearchResponse(
        FriendUserResponse user,
        String relationship,
        Long friendshipId
) {
}
