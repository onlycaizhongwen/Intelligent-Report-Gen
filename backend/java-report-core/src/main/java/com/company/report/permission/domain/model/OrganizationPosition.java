package com.company.report.permission.domain.model;

import java.util.List;

public record OrganizationPosition(
        Long id,
        Long organizationUnitId,
        String code,
        String name,
        List<String> roles,
        Long managerUserId,
        String status,
        int sortOrder
) {
}
