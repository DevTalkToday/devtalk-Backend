package com.example.demo.auth.dto;

import com.example.demo.auth.AppUser;
import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String nickname,
        String email,
        String description,
        String avatarUrl,
        boolean profileCompleted,
        List<String> majors,
        Instant createdAt
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getDescription(),
                user.getAvatarUrl(),
                user.isProfileCompleted(),
                user.getMajors(),
                user.getCreatedAt()
        );
    }
}
