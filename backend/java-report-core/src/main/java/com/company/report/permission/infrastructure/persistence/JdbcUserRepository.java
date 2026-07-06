package com.company.report.permission.infrastructure.persistence;

import com.company.report.permission.domain.model.UserAccount;
import com.company.report.permission.domain.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcUserRepository implements UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserAccount save(UserAccount user) {
        if (user.id() == null || user.id() <= 0) {
            return insert(user);
        }
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    UPDATE user_accounts
                    SET username = ?, display_name = ?, status = ?, department = ?, position = ?, roles = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """);
            statement.setString(1, user.username());
            statement.setString(2, user.displayName());
            statement.setString(3, user.status());
            statement.setString(4, user.department());
            statement.setString(5, user.position());
            statement.setArray(6, connection.createArrayOf("text", user.roles().toArray(String[]::new)));
            statement.setLong(7, user.id());
            return statement;
        });
        return findById(user.id()).orElseThrow();
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        return jdbcTemplate.query("""
                        SELECT id, username, display_name, status, department, position, roles
                        FROM user_accounts
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapUser(rs),
                id
        ).stream().findFirst();
    }

    @Override
    public List<UserAccount> findEnabledByRole(String role) {
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                        SELECT id, username, display_name, status, department, position, roles
                        FROM user_accounts
                        WHERE status = 'enabled'
                          AND ? = ANY(roles)
                        ORDER BY id ASC
                        """,
                (rs, rowNum) -> mapUser(rs),
                role.trim()
        );
    }

    @Override
    public List<UserAccount> findPage(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return jdbcTemplate.query("""
                        SELECT id, username, display_name, status, department, position, roles
                        FROM user_accounts
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> mapUser(rs),
                safePageSize,
                (safePage - 1) * safePageSize
        );
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_accounts", Long.class);
        return count == null ? 0L : count;
    }

    private UserAccount insert(UserAccount user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO user_accounts(username, display_name, status, department, position, roles, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            statement.setString(1, user.username());
            statement.setString(2, user.displayName());
            statement.setString(3, user.status());
            statement.setString(4, user.department());
            statement.setString(5, user.position());
            statement.setArray(6, connection.createArrayOf("text", user.roles().toArray(String[]::new)));
            return statement;
        }, keyHolder);
        return findById(Objects.requireNonNull(keyHolder.getKey()).longValue()).orElseThrow();
    }

    private UserAccount mapUser(ResultSet rs) throws SQLException {
        Array roles = rs.getArray("roles");
        List<String> roleList = roles == null ? List.of() : Arrays.asList((String[]) roles.getArray());
        return new UserAccount(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("department"),
                rs.getString("position"),
                roleList
        );
    }

}
