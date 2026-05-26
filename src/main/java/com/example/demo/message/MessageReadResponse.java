package com.example.demo.message;

public record MessageReadResponse(
        Long userId,
        int readCount
) {
}
