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
                user.isProfileCompleted(),
                user.getMajors(),
                user.getCreatedAt()
        );
    }
}
