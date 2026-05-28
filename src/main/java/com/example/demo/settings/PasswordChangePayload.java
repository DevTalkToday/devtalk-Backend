package com.example.demo.settings;

public record PasswordChangePayload(
        String currentPassword,
        String newPassword,
        String newPasswordConfirm
) {
}
