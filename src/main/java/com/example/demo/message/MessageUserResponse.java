package com.example.demo.message;

import com.example.demo.auth.AppUser;
import java.util.List;

public record MessageUserResponse(
        Long id,
        String username,
        String nickname,
        String description,
        List<String> majors
) {
    public static MessageUserResponse from(AppUser user) {
        return new MessageUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getDescription(),
                List.copyOf(user.getMajors())
        );
    }
}
