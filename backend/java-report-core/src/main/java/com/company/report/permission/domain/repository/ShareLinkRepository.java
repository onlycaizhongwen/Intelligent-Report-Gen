package com.company.report.permission.domain.repository;

import com.company.report.permission.domain.model.ShareLink;

import java.util.Optional;

public interface ShareLinkRepository {
    ShareLink save(ShareLink shareLink);

    Optional<ShareLink> findByToken(String shareToken);
}
