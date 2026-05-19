package com.example.demo.auth.google;

import com.example.demo.auth.dto.GoogleLoginRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GoogleOAuthClient {
    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.google.client-id}") String clientId,
            @Value("${app.google.client-secret}") String clientSecret,
            @Value("${app.google.redirect-uri}") String redirectUri
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public GoogleUserProfile fetchProfile(GoogleLoginRequest request) {
        if (!redirectUri.equals(request.redirectUri())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google redirect URI is not allowed");
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Google OAuth client is not configured");
        }

        GoogleAccessTokenResponse tokenResponse = exchangeCode(request);
        if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
            String message = tokenResponse != null && tokenResponse.errorDescription() != null
                    ? tokenResponse.errorDescription()
                    : "Failed to exchange Google authorization code";
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }

        GoogleUserResponse user = fetchUser(tokenResponse.accessToken());
        if (user == null || user.sub() == null || user.sub().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to read Google user profile");
        }
        if (user.emailVerified() != null && !user.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        }

        return new GoogleUserProfile(user.sub(), user.email(), user.name());
    }

    private GoogleAccessTokenResponse exchangeCode(GoogleLoginRequest request) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", request.code());
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", request.redirectUri());
        if (request.codeVerifier() != null && !request.codeVerifier().isBlank()) {
            body.add("code_verifier", request.codeVerifier());
        }

        try {
            return restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(GoogleAccessTokenResponse.class);
        } catch (RestClientResponseException error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google authorization code exchange failed");
        } catch (ResourceAccessException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to Google");
        }
    }

    private GoogleUserResponse fetchUser(String accessToken) {
        try {
            return restClient.get()
                    .uri("https://openidconnect.googleapis.com/v1/userinfo")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GoogleUserResponse.class);
        } catch (RestClientResponseException error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google user profile request failed");
        } catch (ResourceAccessException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to Google");
        }
    }
}
