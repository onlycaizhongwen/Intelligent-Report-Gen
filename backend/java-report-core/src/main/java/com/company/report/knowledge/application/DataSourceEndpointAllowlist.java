package com.company.report.knowledge.application;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class DataSourceEndpointAllowlist {
    private static final String LOCAL_DEVELOPMENT_ALLOWLIST = String.join(",",
            "localhost",
            "127.0.0.1",
            "::1",
            "host.docker.internal",
            "postgres",
            "mysql",
            "ir-postgres",
            "intelligent-report-system-mysql-1");

    private final boolean allowAll;
    private final Set<String> allowedHosts;

    public DataSourceEndpointAllowlist(String rawAllowlist) {
        Set<String> entries = parseEntries(rawAllowlist);
        this.allowAll = entries.contains("*");
        this.allowedHosts = entries;
    }

    public static DataSourceEndpointAllowlist localDevelopmentDefault() {
        return new DataSourceEndpointAllowlist(LOCAL_DEVELOPMENT_ALLOWLIST);
    }

    public boolean isAllowed(String sourceType, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        if (allowAll || isLocalInMemoryJdbc(endpoint)) {
            return true;
        }
        Set<String> endpointHosts = endpointHosts(sourceType, endpoint);
        if (endpointHosts.isEmpty()) {
            return true;
        }
        return endpointHosts.stream().allMatch(this::hostAllowed);
    }

    private boolean hostAllowed(String endpointHost) {
        String normalized = normalizeHostPattern(endpointHost);
        if (allowedHosts.contains(normalized)) {
            return true;
        }
        String hostOnly = stripPort(normalized);
        if (allowedHosts.contains(hostOnly)) {
            return true;
        }
        for (String allowedHost : allowedHosts) {
            if (allowedHost.startsWith("*.") && hostOnly.endsWith(allowedHost.substring(1))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> parseEntries(String rawAllowlist) {
        Set<String> entries = new LinkedHashSet<>();
        if (rawAllowlist == null || rawAllowlist.isBlank()) {
            return entries;
        }
        for (String rawEntry : rawAllowlist.split("[,;\\s]+")) {
            String entry = rawEntry.trim();
            if (entry.isBlank()) {
                continue;
            }
            if ("*".equals(entry)) {
                entries.add("*");
                continue;
            }
            Set<String> parsedHosts = endpointHosts("api", entry);
            if (parsedHosts.isEmpty()) {
                parsedHosts = endpointHosts("postgresql", entry);
            }
            if (parsedHosts.isEmpty()) {
                entries.add(normalizeHostPattern(entry));
            } else {
                entries.addAll(parsedHosts);
            }
        }
        return entries;
    }

    private static Set<String> endpointHosts(String sourceType, String endpoint) {
        String lowerSourceType = sourceType == null ? "" : sourceType.toLowerCase(Locale.ROOT);
        String trimmedEndpoint = endpoint == null ? "" : endpoint.trim();
        if (trimmedEndpoint.isBlank()) {
            return Set.of();
        }
        if ("api".equals(lowerSourceType) || trimmedEndpoint.startsWith("http://") || trimmedEndpoint.startsWith("https://")) {
            return httpHosts(trimmedEndpoint);
        }
        if (trimmedEndpoint.startsWith("jdbc:postgresql://") || trimmedEndpoint.startsWith("jdbc:mysql://")) {
            return jdbcHosts(trimmedEndpoint);
        }
        return Set.of();
    }

    private static Set<String> httpHosts(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Set.of();
            }
            int port = uri.getPort();
            return Set.of(port > 0 ? normalizeHostPattern(host + ":" + port) : normalizeHostPattern(host));
        } catch (IllegalArgumentException ex) {
            return Set.of();
        }
    }

    private static Set<String> jdbcHosts(String endpoint) {
        String authorityAndPath = endpoint.substring(endpoint.indexOf("//") + 2);
        int slashIndex = authorityAndPath.indexOf('/');
        String authority = slashIndex >= 0 ? authorityAndPath.substring(0, slashIndex) : authorityAndPath;
        int queryIndex = authority.indexOf('?');
        if (queryIndex >= 0) {
            authority = authority.substring(0, queryIndex);
        }
        Set<String> hosts = new LinkedHashSet<>();
        for (String rawHostPort : authority.split(",")) {
            String hostPort = rawHostPort.trim();
            if (!hostPort.isBlank()) {
                hosts.add(normalizeHostPattern(hostPort));
            }
        }
        return hosts;
    }

    private static boolean isLocalInMemoryJdbc(String endpoint) {
        return endpoint.toLowerCase(Locale.ROOT).startsWith("jdbc:h2:mem:");
    }

    private static String normalizeHostPattern(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.contains("]")) {
            int end = normalized.indexOf(']');
            String host = normalized.substring(1, end);
            String suffix = normalized.substring(end + 1);
            return host + suffix;
        }
        return normalized;
    }

    private static String stripPort(String hostPattern) {
        int colonIndex = hostPattern.lastIndexOf(':');
        if (colonIndex <= 0 || hostPattern.indexOf(':') != colonIndex) {
            return hostPattern;
        }
        return hostPattern.substring(0, colonIndex);
    }
}
