package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CompleteProfileRequest(
        @NotBlank @Size(max = 120) String nickname,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(min = 8, max = 100) String passwordConfirm,
        @Size(max = 500) String description,
        List<String> majors
) {
}
