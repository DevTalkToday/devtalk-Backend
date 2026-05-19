package com.example.demo.auth.github;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubAccessTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        String scope,
        String error,
        @JsonProperty("error_description") String errorDescription
) {
}
