package com.example.demo.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AdminAccess {
    public static final String ADMIN_EMAIL = "s25002@gsm.hs.kr";

    private AdminAccess() {
    }

    public static boolean isAdmin(AppUser user) {
        if (user == null) return false;
        return ADMIN_EMAIL.equalsIgnoreCase(nullToBlank(user.getUsername()))
                || ADMIN_EMAIL.equalsIgnoreCase(nullToBlank(user.getEmail()));
    }

    public static void requireAdmin(AppUser user) {
        if (!isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED");
        }
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
