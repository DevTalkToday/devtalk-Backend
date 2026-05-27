package com.example.demo.profile;

import com.example.demo.auth.AppUser;
import java.time.Instant;
import java.util.List;

public record PublicProfileUserResponse(
        Long id,
        String username,
        String email,
        String nickname,
        String description,
        String avatarUrl,
        List<String> majors,
        Instant createdAt
) {
    public static PublicProfileUserResponse from(AppUser user) {
        return new PublicProfileUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getDescription(),
                user.getAvatarUrl(),
                user.getMajors(),
                user.getCreatedAt()
        );
    }
}
