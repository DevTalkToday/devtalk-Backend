package com.example.demo.auth.github;

public record GithubUserProfile(
        String providerUserId,
        String login,
        String name,
        String email
) {
}
