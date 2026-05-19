package com.example.demo.friend;

import jakarta.validation.constraints.NotNull;

public record FriendRequestPayload(@NotNull Long userId) {
}
