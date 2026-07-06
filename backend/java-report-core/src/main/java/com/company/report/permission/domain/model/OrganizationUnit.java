package com.company.report.permission.domain.model;

public record OrganizationUnit(
        Long id,
        String code,
        String name,
        Long parentId,
        String unitType,
        String status,
        int sortOrder
) {
}
