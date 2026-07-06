package com.company.report.permission.infrastructure.persistence;

import com.company.report.permission.domain.model.OrganizationPosition;
import com.company.report.permission.domain.model.OrganizationPositionAssignment;
import com.company.report.permission.domain.model.OrganizationUnit;
import com.company.report.permission.domain.repository.OrganizationDirectoryRepository;

import java.util.List;
import java.util.Optional;

public class NoopOrganizationDirectoryRepository implements OrganizationDirectoryRepository {
    @Override
    public List<OrganizationUnit> findEnabledUnits() {
        return List.of();
    }

    @Override
    public List<OrganizationPosition> findEnabledPositions() {
        return List.of();
    }

    @Override
    public List<OrganizationPositionAssignment> findEnabledPositionAssignments() {
        return List.of();
    }

    @Override
    public Optional<OrganizationPositionAssignment> findPositionAssignmentById(Long assignmentId) {
        return Optional.empty();
    }

    @Override
    public Optional<OrganizationUnit> findUnitById(Long unitId) {
        return Optional.empty();
    }

    @Override
    public OrganizationUnit saveUnit(OrganizationUnit unit) {
        return unit;
    }

    @Override
    public OrganizationUnit updateUnit(OrganizationUnit unit) {
        return unit;
    }

    @Override
    public OrganizationPosition savePosition(OrganizationPosition position) {
        return position;
    }

    @Override
    public OrganizationPositionAssignment savePositionAssignment(OrganizationPositionAssignment assignment) {
        return assignment;
    }

    @Override
    public OrganizationPositionAssignment updatePositionAssignment(OrganizationPositionAssignment assignment) {
        return assignment;
    }

    @Override
    public OrganizationPositionAssignment disablePositionAssignment(Long assignmentId) {
        return new OrganizationPositionAssignment(assignmentId, null, null, false, "disabled", null, null);
    }
}
