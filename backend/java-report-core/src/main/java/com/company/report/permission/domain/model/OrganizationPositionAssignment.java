package com.company.report.permission.domain.model;

import java.time.OffsetDateTime;

public record OrganizationPositionAssignment(
        Long id,
        Long userId,
        Long positionId,
        boolean primary,
        String status,
        OffsetDateTime activeFrom,
        OffsetDateTime activeTo
) {
}
