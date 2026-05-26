package com.example.demo.profile;

import jakarta.validation.constraints.Size;

public record ProfileAvatarPayload(
        @Size(max = 1_500_000)
        String avatarUrl
) {
}
