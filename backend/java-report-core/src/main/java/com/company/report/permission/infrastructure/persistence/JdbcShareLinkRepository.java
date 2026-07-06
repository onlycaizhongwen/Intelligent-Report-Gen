package com.company.report.permission.infrastructure.persistence;

import com.company.report.permission.domain.model.ShareLink;
import com.company.report.permission.domain.repository.ShareLinkRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcShareLinkRepository implements ShareLinkRepository {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcShareLinkRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new ObjectMapper());
    }

    @Autowired
    public JdbcShareLinkRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ShareLink save(ShareLink shareLink) {
        if (shareLink.id() != null && shareLink.id() > 0) {
            jdbcTemplate.update("""
                            UPDATE share_links
                            SET status = ?, password_hash = ?, allow_download = ?, allowed_download_formats = ?::jsonb,
                                max_access_count = ?, allowed_visitors = ?::jsonb, allowed_visitor_domains = ?::jsonb,
                                single_use = ?, expires_at = ?
                            WHERE id = ?
                            """,
                    shareLink.status(),
                    shareLink.passwordHash(),
                    shareLink.allowDownload(),
                    writeJson(shareLink.allowedDownloadFormats()),
                    shareLink.maxAccessCount(),
                    writeJson(shareLink.allowedVisitors()),
                    writeJson(shareLink.allowedVisitorDomains()),
                    shareLink.singleUse(),
                    shareLink.expiresAt(),
                    shareLink.id()
            );
            return shareLink;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO share_links (report_id, created_by, share_token, status, password_hash, allow_download, allowed_download_formats, max_access_count, allowed_visitors, allowed_visitor_domains, single_use, expires_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, ?, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            ps.setLong(1, shareLink.reportId());
            ps.setLong(2, shareLink.createdBy());
            ps.setString(3, shareLink.shareToken());
            ps.setString(4, shareLink.status());
            ps.setString(5, shareLink.passwordHash());
            ps.setBoolean(6, shareLink.allowDownload());
            ps.setString(7, writeJson(shareLink.allowedDownloadFormats()));
            ps.setInt(8, shareLink.maxAccessCount());
            ps.setString(9, writeJson(shareLink.allowedVisitors()));
            ps.setString(10, writeJson(shareLink.allowedVisitorDomains()));
            ps.setBoolean(11, shareLink.singleUse());
            ps.setObject(12, shareLink.expiresAt());
            return ps;
        }, keyHolder);
        return findByToken(shareLink.shareToken()).orElseThrow();
    }

    @Override
    public Optional<ShareLink> findByToken(String shareToken) {
        return jdbcTemplate.query("""
                        SELECT id, report_id, created_by, share_token, status, password_hash, allow_download, allowed_download_formats,
                               max_access_count, allowed_visitors, allowed_visitor_domains, single_use, expires_at, created_at
                        FROM share_links
                        WHERE share_token = ?
                        """,
                (rs, rowNum) -> new ShareLink(
                        rs.getLong("id"),
                        rs.getLong("report_id"),
                        rs.getLong("created_by"),
                        rs.getString("share_token"),
                        rs.getString("status"),
                        rs.getString("password_hash"),
                        rs.getBoolean("allow_download"),
                        readStringList(rs.getString("allowed_download_formats")),
                        rs.getInt("max_access_count"),
                        readStringList(rs.getString("allowed_visitors")),
                        readStringList(rs.getString("allowed_visitor_domains")),
                        rs.getBoolean("single_use"),
                        rs.getObject("expires_at", OffsetDateTime.class),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                shareToken
        ).stream().findFirst();
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize share allowed download formats", ex);
        }
    }

    private List<String> readStringList(String value) {
        try {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to read share allowed download formats", ex);
        }
    }
}
