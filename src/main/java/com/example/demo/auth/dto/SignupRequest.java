package com.example.demo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Size(max = 120) String nickname,
        List<String> majors
) {
}
