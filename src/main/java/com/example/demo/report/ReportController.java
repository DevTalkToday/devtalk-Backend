package com.example.demo.report;

import com.example.demo.auth.AppUser;
import com.example.demo.auth.AuthService;
import com.example.demo.auth.AuthToken;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private final ReportService reportService;
    private final AuthService authService;

    public ReportController(ReportService reportService, AuthService authService) {
        this.reportService = reportService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<ReportSubmissionResponse> submit(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody ReportPayload payload
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.submit(payload, resolveReporter(authorization)));
    }

    @GetMapping
    public List<ReportResponse> list(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        AppUser actor = authService.authenticate(readBearerToken(authorization));
        return reportService.list(actor, limit);
    }

    private AppUser resolveReporter(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) return null;

        try {
            AuthToken authToken = authService.authenticateToken(token);
            return authToken.isGuest() ? null : authToken.getUser();
        } catch (ResponseStatusException ignored) {
            return null;
        }
    }

    private static String readBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        return token;
    }
}
