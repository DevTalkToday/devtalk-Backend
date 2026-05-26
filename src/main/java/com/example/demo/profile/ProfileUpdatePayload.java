package com.example.demo.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProfileUpdatePayload(
        @NotBlank
        @Size(max = 120)
        String nickname,
        @Size(max = 500)
        String description,
        List<@Size(max = 80) String> majors
) {
}
