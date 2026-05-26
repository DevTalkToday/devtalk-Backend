package com.example.demo.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MessagePayload(
        @NotNull Long recipientId,
        @NotBlank @Size(max = 2000) String body
) {
}
