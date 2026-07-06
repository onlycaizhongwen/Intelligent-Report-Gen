package com.company.report.permission.domain.repository;

import com.company.report.permission.domain.model.OrganizationPosition;
import com.company.report.permission.domain.model.OrganizationPositionAssignment;
import com.company.report.permission.domain.model.OrganizationUnit;

import java.util.List;
import java.util.Optional;

public interface OrganizationDirectoryRepository {
    List<OrganizationUnit> findEnabledUnits();

    List<OrganizationPosition> findEnabledPositions();

    List<OrganizationPositionAssignment> findEnabledPositionAssignments();

    Optional<OrganizationPositionAssignment> findPositionAssignmentById(Long assignmentId);

    Optional<OrganizationUnit> findUnitById(Long unitId);

    OrganizationUnit saveUnit(OrganizationUnit unit);

    OrganizationUnit updateUnit(OrganizationUnit unit);

    OrganizationPosition savePosition(OrganizationPosition position);

    OrganizationPositionAssignment savePositionAssignment(OrganizationPositionAssignment assignment);

    OrganizationPositionAssignment updatePositionAssignment(OrganizationPositionAssignment assignment);

    OrganizationPositionAssignment disablePositionAssignment(Long assignmentId);
}
