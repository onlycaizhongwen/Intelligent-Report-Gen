package com.company.report.permission.domain.repository;

import com.company.report.permission.domain.model.UserAccount;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    UserAccount save(UserAccount user);

    Optional<UserAccount> findById(Long id);

    List<UserAccount> findEnabledByRole(String role);

    List<UserAccount> findPage(int page, int pageSize);

    long count();
}
