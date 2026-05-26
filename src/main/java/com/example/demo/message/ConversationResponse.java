package com.example.demo.message;

public record ConversationResponse(
        MessageUserResponse user,
        MessageResponse lastMessage,
        long unreadCount
) {
}
