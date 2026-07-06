package com.company.report.shared.security;

import java.util.Set;

public record CurrentUser(Long userId, Set<String> roles, Set<String> permissions, String status) {
    public CurrentUser(Long userId, Set<String> roles, Set<String> permissions) {
        this(userId, roles, permissions, "enabled");
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean enabled() {
        return status == null || status.isBlank() || "enabled".equals(status);
    }
}
