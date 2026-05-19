package com.example.demo.auth.google;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleAccessTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn,
        String scope,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("id_token") String idToken,
        String error,
        @JsonProperty("error_description") String errorDescription
) {
}
