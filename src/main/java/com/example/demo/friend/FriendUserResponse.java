package com.example.demo.friend;

import com.example.demo.auth.AppUser;
import java.util.List;

public record FriendUserResponse(
        Long id,
        String username,
        String nickname,
        String description,
        List<String> majors,
        String avatarUrl
) {
    public static FriendUserResponse from(AppUser user) {
        return new FriendUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getDescription(),
                user.getMajors(),
                user.getAvatarUrl()
        );
    }
}
