package com.company.report.permission.domain.model;

import java.util.List;

public record UserAccount(
        Long id,
        String username,
        String displayName,
        String status,
        String department,
        String position,
        List<String> roles
) {
    public UserAccount(Long id, String username, String displayName, String status, List<String> roles) {
        this(id, username, displayName, status, "", "", roles);
    }

    public static UserAccount enabled(String username, String displayName, List<String> roles) {
        return enabled(username, displayName, "", "", roles);
    }

    public static UserAccount enabled(String username, String displayName, String department, String position, List<String> roles) {
        return new UserAccount(null, username, displayName, "enabled", clean(department), clean(position), roles == null ? List.of("viewer") : roles);
    }

    public UserAccount withId(Long id) {
        return new UserAccount(id, username, displayName, status, department, position, roles);
    }

    public UserAccount withStatus(String status) {
        return new UserAccount(id, username, displayName, status, department, position, roles);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
