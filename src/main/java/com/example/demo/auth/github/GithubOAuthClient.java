package com.example.demo.auth.github;

import com.example.demo.auth.dto.GithubLoginRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParseException;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GithubOAuthClient {
    private static final Logger logger = LoggerFactory.getLogger(GithubOAuthClient.class);
    private static final String USER_AGENT = "Devtalk/1.0";
    private static final Duration DEFAULT_PROCESS_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration EMAIL_PROCESS_TIMEOUT = Duration.ofSeconds(5);

    private final JsonParser jsonParser;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GithubOAuthClient(
            @Value("${app.github.client-id}") String clientId,
            @Value("${app.github.client-secret}") String clientSecret,
            @Value("${app.github.redirect-uri}") String redirectUri
    ) {
        this.jsonParser = JsonParserFactory.getJsonParser();
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
        List<String> entries = new ArrayList<>();
        entries.add(formEntry("client_id", clientId));
        entries.add(formEntry("client_secret", clientSecret));
        entries.add(formEntry("code", request.code()));
        entries.add(formEntry("redirect_uri", request.redirectUri()));
        if (request.codeVerifier() != null && !request.codeVerifier().isBlank()) {
            entries.add(formEntry("code_verifier", request.codeVerifier()));
        }

        CurlResponse response = runCurl(List.of(
                "-H", "Accept: application/json",
                "-H", "Content-Type: application/x-www-form-urlencoded",
                "-X", "POST",
                "--data", String.join("&", entries),
                "https://github.com/login/oauth/access_token"
        ), "GitHub token exchange", DEFAULT_PROCESS_TIMEOUT);

        if (response.statusCode() >= 400) {
            logger.warn("GitHub token exchange failed with status {} and body {}", response.statusCode(), response.body());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "GitHub authorization code exchange failed");
        }

        return readAccessTokenResponse(response.body(), "GitHub authorization code exchange failed");
    }

    private GithubUserResponse fetchUser(String accessToken) {
        CurlResponse response = runCurl(List.of(
                "-H", "Accept: application/json",
                "-H", "Authorization: Bearer " + accessToken,
                "-H", "X-GitHub-Api-Version: 2022-11-28",
                "https://api.github.com/user"
        ), "GitHub user profile request", DEFAULT_PROCESS_TIMEOUT);

        if (response.statusCode() >= 400) {
            logger.warn("GitHub user profile request failed with status {} and body {}", response.statusCode(), response.body());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "GitHub user profile request failed");
        }

        return readUserResponse(response.body(), "GitHub user profile request failed");
    }

    private String fetchPrimaryEmail(String accessToken) {
        try {
            CurlResponse response = runCurl(List.of(
                    "-H", "Accept: application/json",
                    "-H", "Authorization: Bearer " + accessToken,
                    "-H", "X-GitHub-Api-Version: 2022-11-28",
                    "https://api.github.com/user/emails"
            ), "GitHub email request", EMAIL_PROCESS_TIMEOUT);

            if (response.statusCode() >= 400) {
                logger.warn("GitHub email request failed with status {} and body {}", response.statusCode(), response.body());
                return null;
            }

            GithubEmailResponse[] emails = readEmailResponses(response.body(), "GitHub email request failed");
            if (emails == null) return null;
            for (GithubEmailResponse email : emails) {
                if (email.primary() && email.verified()) return email.email();
            }
            for (GithubEmailResponse email : emails) {
                if (email.verified()) return email.email();
            }
            return null;
        } catch (ResponseStatusException error) {
            logger.warn("GitHub email request failed: {}", error.getReason());
            return null;
        }
    }

    private CurlResponse runCurl(List<String> args, String operation, Duration timeout) {
        List<String> command = new ArrayList<>();
        command.add("curl");
        command.add("-sS");
        command.add("-L");
        command.add("--ipv4");
        command.add("--connect-timeout");
        command.add(String.valueOf(Math.max(3L, timeout.toSeconds() / 2)));
        command.add("--max-time");
        command.add(String.valueOf(timeout.toSeconds()));
        command.add("-A");
        command.add(USER_AGENT);
        command.addAll(args);
        command.add("-w");
        command.add("\n%{http_code}");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeout.toSeconds() + 2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("{} connection failure: curl process timed out", operation);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to GitHub");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                logger.warn("{} connection failure: curl exited with {} and output {}", operation, process.exitValue(), output);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to GitHub");
            }

            return parseCurlResponse(output, operation);
        } catch (IOException error) {
            logger.warn("{} connection failure: {}", operation, error.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to GitHub");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            logger.warn("{} interrupted: {}", operation, error.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to GitHub");
        }
    }

    private CurlResponse parseCurlResponse(String output, String operation) {
        int separator = output.lastIndexOf('\n');
        if (separator < 0) {
            logger.warn("{} returned unexpected output: {}", operation, output);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to GitHub");
        }

        String body = output.substring(0, separator);
        String statusText = output.substring(separator + 1).trim();
        try {
            return new CurlResponse(Integer.parseInt(statusText), body);
        } catch (NumberFormatException error) {
            logger.warn("{} returned invalid status marker: {}", operation, output);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to GitHub");
        }
    }

    private static String formEntry(String key, String value) {
        return java.net.URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "="
                + java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private GithubAccessTokenResponse readAccessTokenResponse(String body, String failureMessage) {
        try {
            Map<String, Object> payload = jsonParser.parseMap(body);
            return new GithubAccessTokenResponse(
                    asString(payload.get("access_token")),
                    asString(payload.get("token_type")),
                    asString(payload.get("scope")),
                    asString(payload.get("error")),
                    asString(payload.get("error_description"))
            );
        } catch (JsonParseException error) {
            logger.warn("{} - invalid JSON body: {}", failureMessage, body);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, failureMessage);
        }
    }

    private GithubUserResponse readUserResponse(String body, String failureMessage) {
        try {
            Map<String, Object> payload = jsonParser.parseMap(body);
            return new GithubUserResponse(
                    asLong(payload.get("id")),
                    asString(payload.get("login")),
                    asString(payload.get("name")),
                    asString(payload.get("email"))
            );
        } catch (JsonParseException error) {
            logger.warn("{} - invalid JSON body: {}", failureMessage, body);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, failureMessage);
        }
    }

    private GithubEmailResponse[] readEmailResponses(String body, String failureMessage) {
        try {
            List<Object> payload = jsonParser.parseList(body);
            List<GithubEmailResponse> emails = new ArrayList<>();
            for (Object item : payload) {
                if (!(item instanceof Map<?, ?> map)) continue;
                emails.add(new GithubEmailResponse(
                        asString(map.get("email")),
                        asBoolean(map.get("primary")),
                        asBoolean(map.get("verified")),
                        asString(map.get("visibility"))
                ));
            }
            return emails.toArray(GithubEmailResponse[]::new);
        } catch (JsonParseException error) {
            logger.warn("{} - invalid JSON body: {}", failureMessage, body);
            return null;
        }
    }

    private static String asString(Object value) {
        return value instanceof String str ? str : null;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String str && !str.isBlank()) return Long.parseLong(str);
        return null;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String str) return Boolean.parseBoolean(str);
        return false;
    }

    private record CurlResponse(int statusCode, String body) {
    }
}
