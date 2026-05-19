package com.example.demo.auth.google;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleUserResponse(
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified,
        String name,
        @JsonProperty("given_name") String givenName,
        String picture
) {
}
