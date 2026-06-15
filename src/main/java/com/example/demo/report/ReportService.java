package com.example.demo.report;

import com.example.demo.auth.AdminAccess;
import com.example.demo.auth.AppUser;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;
    private static final Set<String> TARGET_TYPES = Set.of("post", "comment", "profile");

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Transactional
    public ReportSubmissionResponse submit(ReportPayload payload, AppUser reporter) {
        String targetType = normalizeTargetType(payload == null ? null : payload.targetType());
        String targetId = clamp(payload == null ? null : payload.targetId(), 80);
        String subject = clamp(payload == null ? null : payload.subject(), 100);
        String content = clamp(payload == null ? null : payload.content(), 1200);

        if (!TARGET_TYPES.contains(targetType) || targetId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REPORT_TARGET_REQUIRED");
        }
        if (subject.isBlank() || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REPORT_CONTENT_REQUIRED");
        }

        Report report = reportRepository.save(new Report(
                targetType,
                targetId,
                clamp(payload.targetLabel(), 120),
                sanitizeTargetUrl(payload.targetUrl()),
                subject,
                content,
                reporter == null ? null : reporter.getId(),
                reporter == null ? null : reporterLabel(reporter)
        ));

        return new ReportSubmissionResponse(report.getId(), "submitted", report.getCreatedAt());
    }

    @Transactional
    public List<ReportResponse> list(AppUser actor, int limit) {
        AdminAccess.requireAdmin(actor);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        if (safeLimit <= 0) safeLimit = DEFAULT_LIMIT;

        return reportRepository.findByOrderByCreatedAtDescIdDesc(PageRequest.of(0, safeLimit))
                .stream()
                .map(ReportResponse::from)
                .toList();
    }

    private static String reporterLabel(AppUser reporter) {
        String nickname = nullToBlank(reporter.getNickname());
        String username = nullToBlank(reporter.getUsername());
        if (!nickname.isBlank() && !username.isBlank()) return nickname + " (" + username + ")";
        if (!nickname.isBlank()) return nickname;
        return username.isBlank() ? "Unknown user" : username;
    }

    private static String normalizeTargetType(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String clamp(String value, int max) {
        String normalized = trim(value);
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String sanitizeTargetUrl(String value) {
        String normalized = clamp(value, 300);
        if (normalized.isBlank() || normalized.chars().anyMatch(Character::isISOControl)) {
            return "";
        }

        try {
            URI uri = URI.create(normalized);
            if (!uri.isAbsolute()) {
                return normalized.startsWith("/") && !normalized.startsWith("//") ? normalized : "";
            }

            String scheme = nullToBlank(uri.getScheme()).toLowerCase(Locale.ROOT);
            return "http".equals(scheme) || "https".equals(scheme) ? uri.toString() : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
