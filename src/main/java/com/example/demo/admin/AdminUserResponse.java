package com.example.demo.admin;

import com.example.demo.auth.AdminAccess;
import com.example.demo.auth.AppUser;
import java.time.Instant;
import java.util.List;

public record AdminUserResponse(
        Long id,
        String username,
        String nickname,
        String email,
        boolean profileCompleted,
        boolean admin,
        List<String> majors,
        long postCount,
        long commentCount,
        Instant createdAt
) {
    public static AdminUserResponse from(AppUser user, long postCount, long commentCount) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.isProfileCompleted(),
                AdminAccess.isAdmin(user),
                user.getMajors(),
                postCount,
                commentCount,
                user.getCreatedAt()
        );
    }
}
