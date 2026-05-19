package com.example.demo.post;

public record PostAuthorResponse(
        String id,
        String nickname,
        String role,
        String avatarUrl
) {
}
