package com.company.report.permission.infrastructure.persistence;

import com.company.report.permission.domain.model.UserAccount;
import com.company.report.permission.domain.repository.UserRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryUserRepository implements UserRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, UserAccount> users = new LinkedHashMap<>();

    @Override
    public synchronized UserAccount save(UserAccount user) {
        UserAccount saved = user.id() == null || user.id() <= 0 ? user.withId(ids.getAndIncrement()) : user;
        users.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized Optional<UserAccount> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public synchronized List<UserAccount> findEnabledByRole(String role) {
        String requestedRole = role == null ? "" : role.trim();
        if (requestedRole.isBlank()) {
            return List.of();
        }
        return users.values().stream()
                .filter(user -> "enabled".equals(user.status()))
                .filter(user -> user.roles().contains(requestedRole))
                .sorted(Comparator.comparing(UserAccount::id))
                .toList();
    }

    @Override
    public synchronized List<UserAccount> findPage(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return users.values().stream()
                .sorted(Comparator.comparing(UserAccount::id).reversed())
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public synchronized long count() {
        return users.size();
    }
}
