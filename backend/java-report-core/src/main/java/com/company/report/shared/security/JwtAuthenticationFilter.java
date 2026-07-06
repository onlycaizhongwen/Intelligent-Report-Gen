package com.company.report.shared.security;

import com.company.report.shared.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublicPath(request.getMethod(), path) || path.startsWith("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }

        try {
            CurrentUser currentUser = tokenProvider.parse(authorization.substring(7));
            if (!currentUser.enabled()) {
                writeForbidden(response);
                return;
            }
            CurrentUserHolder.set(currentUser);
            var authorities = currentUser.permissions().stream().map(SimpleGrantedAuthority::new).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(currentUser.userId(), null, authorities)
            );
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            writeUnauthorized(response);
        } finally {
            CurrentUserHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(401, "请登录后继续操作"));
    }
    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(403, "当前账号无权执行该操作"));
    }

    private boolean isPublicPath(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        if (!path.startsWith("/api/v1/share-links/")) {
            return false;
        }
        return path.matches("^/api/v1/share-links/[^/]+/(access|report)$")
                || path.matches("^/api/v1/share-links/[^/]+/exports/[^/]+/download-url$");
    }
}
