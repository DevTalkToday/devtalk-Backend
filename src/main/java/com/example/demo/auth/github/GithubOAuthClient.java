package com.example.demo.auth.github;

import com.example.demo.auth.dto.GithubLoginRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class GithubOAuthClient {
    private static final Logger logger = LoggerFactory.getLogger(GithubOAuthClient.class);

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GithubOAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.github.client-id}") String clientId,
            @Value("${app.github.client-secret}") String clientSecret,
            @Value("${app.github.redirect-uri}") String redirectUri
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public GithubUserProfile fetchProfile(GithubLoginRequest request) {
        if (!redirectUri.equals(request.redirectUri())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub redirect URI is not allowed");
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "GitHub OAuth client is not configured");
        }

        GithubAccessTokenResponse tokenResponse = exchangeCode(request);
        if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
            String message = tokenResponse != null && tokenResponse.errorDescription() != null
                    ? tokenResponse.errorDescription()
                    : "Failed to exchange GitHub authorization code";
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }

        GithubUserResponse user = fetchUser(tokenResponse.accessToken());
        if (user == null || user.id() == null || user.login() == null || user.login().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to read GitHub user profile");
        }

        String email = user.email() != null && !user.email().isBlank()
                ? user.email()
                : fetchPrimaryEmail(tokenResponse.accessToken());

        return new GithubUserProfile(
                String.valueOf(user.id()),
                user.login(),
                user.name(),
                email
        );
    }

    private GithubAccessTokenResponse exchangeCode(GithubLoginRequest request) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", request.code());
        body.add("redirect_uri", request.redirectUri());
        if (request.codeVerifier() != null && !request.codeVerifier().isBlank()) {
            body.add("code_verifier", request.codeVerifier());
        }

        try {
            return restClient.post()
                    .uri("https://github.com/login/oauth/access_token")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(GithubAccessTokenResponse.class);
        } catch (RestClientResponseException error) {
            logger.warn("GitHub token exchange failed with status {} and body {}", error.getStatusCode(), error.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "GitHub authorization code exchange failed");
        } catch (ResourceAccessException error) {
            logger.warn("GitHub token exchange connection failure: {}", error.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to GitHub");
        }
    }

    private GithubUserResponse fetchUser(String accessToken) {
        try {
            return restClient.get()
                    .uri("https://api.github.com/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GithubUserResponse.class);
        } catch (RestClientResponseException error) {
            logger.warn("GitHub user profile request failed with status {} and body {}", error.getStatusCode(), error.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "GitHub user profile request failed");
        } catch (ResourceAccessException error) {
            logger.warn("GitHub user profile request connection failure: {}", error.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to GitHub");
        }
    }

    private String fetchPrimaryEmail(String accessToken) {
        try {
            GithubEmailResponse[] emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GithubEmailResponse[].class);
            if (emails == null) return null;
            for (GithubEmailResponse email : emails) {
                if (email.primary() && email.verified()) return email.email();
            }
            for (GithubEmailResponse email : emails) {
                if (email.verified()) return email.email();
            }
            return null;
        } catch (RestClientResponseException error) {
            logger.warn("GitHub email request failed with status {} and body {}", error.getStatusCode(), error.getResponseBodyAsString());
            return null;
        } catch (ResourceAccessException error) {
            logger.warn("GitHub email request connection failure: {}", error.getMessage());
            return null;
        } catch (ResponseStatusException error) {
            logger.warn("GitHub email request failed: {}", error.getReason());
            return null;
        }
    }
}
