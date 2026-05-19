package com.example.demo.auth.github;

public record GithubEmailResponse(
        String email,
        boolean primary,
        boolean verified,
        String visibility
) {
}
