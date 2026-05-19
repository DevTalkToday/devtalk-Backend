package com.example.demo.auth.github;

public record GithubUserResponse(
        Long id,
        String login,
        String name,
        String email
) {
}
