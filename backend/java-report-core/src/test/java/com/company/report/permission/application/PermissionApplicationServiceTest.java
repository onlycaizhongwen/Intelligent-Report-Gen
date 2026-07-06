package com.company.report.permission.application;

import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.permission.domain.model.ShareLink;
import com.company.report.permission.domain.model.OrganizationPosition;
import com.company.report.permission.domain.model.OrganizationPositionAssignment;
import com.company.report.permission.domain.model.OrganizationUnit;
import com.company.report.permission.domain.repository.UserRepository;
import com.company.report.permission.domain.repository.OrganizationDirectoryRepository;
import com.company.report.permission.infrastructure.persistence.InMemoryUserRepository;
import com.company.report.permission.domain.repository.ShareLinkRepository;
import com.company.report.report.domain.model.Report;
import com.company.report.report.domain.model.ReportStatus;
import com.company.report.report.domain.repository.ReportContentRepository;
import com.company.report.report.domain.repository.ReportExportFileRepository;
import com.company.report.report.domain.repository.ReportExportStorage;
import com.company.report.report.domain.repository.ReportRepository;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionApplicationServiceTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Pattern REQUIRES_PERMISSION = Pattern.compile("@RequiresPermission\\(\"([^\"]+)\"\\)");

    @Test
    void createsListsAndUpdatesUsersFromRepositoryState() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                userRepository
        );

        Map<String, Object> created = service.addUser(Map.of(
                "username", "analyst.one",
                "displayName", "Analyst One",
                "department", "Finance Center",
                "position", "Senior Analyst",
                "roles", java.util.List.of("analyst")
        ));
        Long userId = ((Number) created.get("userId")).longValue();
        var listed = service.users(1, 10);
        Map<String, Object> disabled = service.updateStatus(userId, Map.of("status", "disabled"));

        assertThat(created)
                .containsEntry("username", "analyst.one")
                .containsEntry("displayName", "Analyst One")
                .containsEntry("department", "Finance Center")
                .containsEntry("position", "Senior Analyst")
                .containsEntry("status", "enabled");
        assertThat(listed.items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("userId", userId)
                .containsEntry("username", "analyst.one")
                .containsEntry("department", "Finance Center")
                .containsEntry("position", "Senior Analyst")
                .containsEntry("status", "enabled");
        assertThat(disabled)
                .containsEntry("userId", userId)
                .containsEntry("status", "disabled");
        assertThat(service.users(1, 10).items())
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "disabled");
    }

    @Test
    void batchImportsUsersAndReportsDuplicatesWithoutOverwritingExistingAccounts() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                userRepository
        );
        service.addUser(Map.of(
                "username", "existing.viewer",
                "displayName", "Existing Viewer",
                "roles", List.of("viewer")
        ));

        Map<String, Object> result = service.batchImportUsers(Map.of(
                "users", List.of(
                        Map.of(
                                "username", "analyst.one",
                                "displayName", "Analyst One",
                                "department", "Finance Center",
                                "position", "Senior Analyst",
                                "roles", List.of("analyst")
                        ),
                        Map.of("username", "existing.viewer", "displayName", "Duplicate Viewer", "roles", List.of("analyst")),
                        Map.of("username", "  ", "displayName", "Missing Username", "roles", List.of("viewer")),
                        Map.of("username", "viewer.one", "displayName", "Viewer One", "roles", List.of("viewer"))
                )
        ));

        assertThat(result)
                .containsEntry("imported", 2)
                .containsEntry("failed", 2);
        assertThat(result.get("items"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .hasSize(4)
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("username", "analyst.one")
                        .containsEntry("department", "Finance Center")
                        .containsEntry("position", "Senior Analyst")
                        .containsEntry("status", "imported"))
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("username", "existing.viewer")
                        .containsEntry("status", "failed")
                        .containsEntry("reason", "duplicate_username"))
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("status", "failed")
                        .containsEntry("reason", "username_required"));
        assertThat(service.users(1, 10).items())
                .extracting(user -> String.valueOf(user.get("username")))
                .containsExactly("viewer.one", "analyst.one", "existing.viewer");
        assertThat(userRepository.findEnabledByRole("analyst"))
                .singleElement()
                .satisfies(user -> {
                    assertThat(user.department()).isEqualTo("Finance Center");
                    assertThat(user.position()).isEqualTo("Senior Analyst");
                });
    }

    @Test
    void buildsOrganizationDirectoryFromEnabledUsers() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                userRepository
        );
        service.addUser(Map.of(
                "username", "fin.manager",
                "displayName", "Fiona Manager",
                "department", "Finance Center",
                "position", "Finance Manager",
                "roles", List.of("finance_manager")
        ));
        service.addUser(Map.of(
                "username", "fin.delegate",
                "displayName", "Derek Delegate",
                "department", "Finance Center",
                "position", "Backup Approver",
                "roles", List.of("finance_delegate", "viewer")
        ));
        service.addUser(Map.of(
                "username", "legal.manager",
                "displayName", "Laura Legal",
                "department", "Legal Center",
                "position", "Legal Manager",
                "roles", List.of("legal_manager")
        ));
        Map<String, Object> disabled = service.addUser(Map.of(
                "username", "disabled.manager",
                "displayName", "Disabled Manager",
                "department", "Finance Center",
                "position", "Finance Manager",
                "roles", List.of("finance_manager")
        ));
        service.updateStatus(((Number) disabled.get("userId")).longValue(), Map.of("status", "disabled"));

        Map<String, Object> directory = service.organizationDirectory();

        assertThat(directory.get("departments"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("department", "Finance Center")
                .satisfies(department -> assertThat(department.get("positions"))
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                        .anySatisfy(position -> assertThat(position)
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                .containsEntry("position", "Backup Approver")
                                .satisfies(item -> {
                                    assertThat(item.get("roles")).asList().containsExactly("finance_delegate", "viewer");
                                    assertThat(item.get("users"))
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                            .singleElement()
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                            .containsEntry("username", "fin.delegate")
                                            .containsEntry("displayName", "Derek Delegate")
                                            .doesNotContainKey("password");
                                }))
                        .anySatisfy(position -> assertThat(position)
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                .containsEntry("position", "Finance Manager")
                                .satisfies(item -> assertThat(item.get("users"))
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                        .singleElement()
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                        .containsEntry("username", "fin.manager"))));
        assertThat(directory.get("roles"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .extracting(role -> String.valueOf(((Map<?, ?>) role).get("role")))
                .containsExactly("finance_delegate", "finance_manager", "legal_manager", "viewer");
        assertThat(directory.toString()).doesNotContain("disabled.manager");
    }

    @Test
    void includesPersistedOrganizationTreeInOrganizationDirectory() {
        InMemoryOrganizationDirectoryRepository organizationRepository = new InMemoryOrganizationDirectoryRepository(
                List.of(
                        new OrganizationUnit(1L, "HQ", "Headquarters", null, "company", "enabled", 10),
                        new OrganizationUnit(2L, "FIN", "Finance Center", 1L, "department", "enabled", 20),
                        new OrganizationUnit(3L, "FIN-RISK", "Finance Risk Team", 2L, "team", "enabled", 30),
                        new OrganizationUnit(4L, "DISABLED", "Disabled Office", 1L, "department", "disabled", 40)
                ),
                List.of(
                        new OrganizationPosition(10L, 2L, "finance_manager", "Finance Manager", List.of("finance_manager"), 71L, "enabled", 10),
                        new OrganizationPosition(11L, 3L, "risk_approver", "Risk Approver", List.of("risk_manager", "finance_delegate"), 72L, "enabled", 20),
                        new OrganizationPosition(12L, 4L, "disabled_position", "Disabled Position", List.of("disabled_role"), null, "enabled", 30)
                )
        );
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                new InMemoryUserRepository(),
                new InMemoryAuditRepository(),
                organizationRepository
        );

        Map<String, Object> directory = service.organizationDirectory();

        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "HQ")
                .containsEntry("name", "Headquarters")
                .satisfies(root -> assertThat(root.get("children"))
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                        .singleElement()
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("code", "FIN")
                        .containsEntry("name", "Finance Center")
                        .satisfies(finance -> {
                            assertThat(finance.get("positions"))
                                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                    .singleElement()
                                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                    .containsEntry("code", "finance_manager")
                                    .containsEntry("name", "Finance Manager")
                                    .containsEntry("managerUserId", 71L);
                            assertThat(finance.get("children"))
                                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                    .singleElement()
                                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                    .containsEntry("code", "FIN-RISK")
                                    .satisfies(team -> assertThat(team.get("positions"))
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                            .singleElement()
                                            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                            .containsEntry("code", "risk_approver")
                                            .satisfies(position -> assertThat(position.get("roles"))
                                                    .asList()
                                                    .containsExactly("risk_manager", "finance_delegate")));
                        }));
        assertThat(directory.toString()).doesNotContain("Disabled Office", "disabled_role");
    }

    @Test
    void createsOrganizationUnitAndPositionForDirectoryMaintenance() {
        InMemoryOrganizationDirectoryRepository organizationRepository = new InMemoryOrganizationDirectoryRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                new InMemoryUserRepository(),
                new InMemoryAuditRepository(),
                organizationRepository
        );

        Map<String, Object> root = service.createOrganizationUnit(Map.of(
                "code", "HQ",
                "name", "Headquarters",
                "unitType", "company",
                "sortOrder", 10
        ));
        Map<String, Object> finance = service.createOrganizationUnit(Map.of(
                "code", "FIN",
                "name", "Finance Center",
                "parentId", root.get("unitId"),
                "unitType", "department",
                "sortOrder", 20
        ));
        Map<String, Object> position = service.createOrganizationPosition(Map.of(
                "organizationUnitId", finance.get("unitId"),
                "code", "finance_manager",
                "name", "Finance Manager",
                "roles", List.of("finance_manager"),
                "managerUserId", 71,
                "sortOrder", 10
        ));

        Map<String, Object> directory = service.organizationDirectory();

        assertThat(root)
                .containsEntry("code", "HQ")
                .containsEntry("name", "Headquarters")
                .containsEntry("unitType", "company");
        assertThat(finance)
                .containsEntry("code", "FIN")
                .containsEntry("parentId", root.get("unitId"));
        assertThat(position)
                .containsEntry("code", "finance_manager")
                .containsEntry("organizationUnitId", finance.get("unitId"))
                .containsEntry("managerUserId", 71L);
        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "HQ")
                .satisfies(item -> assertThat(item.get("children"))
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                        .singleElement()
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("code", "FIN")
                        .satisfies(child -> assertThat(child.get("positions"))
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                .singleElement()
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("code", "finance_manager")));
    }

    @Test
    void updatesOrganizationUnitHierarchyForDirectoryMaintenance() {
        InMemoryOrganizationDirectoryRepository organizationRepository = new InMemoryOrganizationDirectoryRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                new InMemoryUserRepository(),
                new InMemoryAuditRepository(),
                organizationRepository
        );
        Map<String, Object> root = service.createOrganizationUnit(Map.of(
                "code", "HQ",
                "name", "Headquarters",
                "unitType", "company",
                "sortOrder", 10
        ));
        Map<String, Object> finance = service.createOrganizationUnit(Map.of(
                "code", "FIN",
                "name", "Finance Center",
                "parentId", root.get("unitId"),
                "unitType", "department",
                "sortOrder", 20
        ));
        Map<String, Object> risk = service.createOrganizationUnit(Map.of(
                "code", "RISK",
                "name", "Risk Office",
                "parentId", root.get("unitId"),
                "unitType", "department",
                "sortOrder", 30
        ));

        Map<String, Object> updated = service.updateOrganizationUnit(((Number) risk.get("unitId")).longValue(), Map.of(
                "name", "Finance Risk Team",
                "parentId", finance.get("unitId"),
                "unitType", "team",
                "sortOrder", 5
        ));
        Map<String, Object> directory = service.organizationDirectory();

        assertThat(updated)
                .containsEntry("unitId", risk.get("unitId"))
                .containsEntry("code", "RISK")
                .containsEntry("name", "Finance Risk Team")
                .containsEntry("parentId", finance.get("unitId"))
                .containsEntry("unitType", "team");
        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "HQ")
                .satisfies(item -> assertThat(item.get("children"))
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                        .singleElement()
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("code", "FIN")
                        .satisfies(child -> assertThat(child.get("children"))
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                .singleElement()
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                .containsEntry("code", "RISK")
                                .containsEntry("name", "Finance Risk Team")));
    }

    @Test
    void assignsUsersToOrganizationPositionsForDirectoryAggregation() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryOrganizationDirectoryRepository organizationRepository = new InMemoryOrganizationDirectoryRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                userRepository,
                new InMemoryAuditRepository(),
                organizationRepository
        );
        Map<String, Object> user = service.addUser(Map.of(
                "username", "fin.assigned",
                "displayName", "Assigned Finance User",
                "roles", List.of("viewer")
        ));
        Map<String, Object> root = service.createOrganizationUnit(Map.of(
                "code", "HQ",
                "name", "Headquarters",
                "unitType", "company",
                "sortOrder", 10
        ));
        Map<String, Object> finance = service.createOrganizationUnit(Map.of(
                "code", "FIN",
                "name", "Finance Center",
                "parentId", root.get("unitId"),
                "unitType", "department",
                "sortOrder", 20
        ));
        Map<String, Object> position = service.createOrganizationPosition(Map.of(
                "organizationUnitId", finance.get("unitId"),
                "code", "finance_manager",
                "name", "Finance Manager",
                "roles", List.of("finance_manager", "approval_owner"),
                "sortOrder", 10
        ));

        Map<String, Object> assignment = service.assignUserToOrganizationPosition(Map.of(
                "userId", user.get("userId"),
                "positionId", position.get("positionId"),
                "primary", true
        ));
        Map<String, Object> directory = service.organizationDirectory();

        assertThat(assignment)
                .containsEntry("userId", user.get("userId"))
                .containsEntry("positionId", position.get("positionId"))
                .containsEntry("primary", true);
        assertThat(directory.get("departments"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("department", "Finance Center")
                .satisfies(department -> assertThat(department.get("positions"))
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                        .singleElement()
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("position", "Finance Manager")
                        .satisfies(item -> {
                            assertThat(item.get("roles")).asList().containsExactly("approval_owner", "finance_manager");
                            assertThat(item.get("users"))
                                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                    .singleElement()
                                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                    .containsEntry("username", "fin.assigned")
                                    .containsEntry("department", "Finance Center")
                                    .containsEntry("position", "Finance Manager");
                        }));
        assertThat(directory.get("roles"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .filteredOn(role -> "finance_manager".equals(((Map<?, ?>) role).get("role")))
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("department", "Finance Center")
                .containsEntry("position", "Finance Manager")
                .satisfies(role -> assertThat(role.get("users"))
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                        .singleElement()
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("username", "fin.assigned"));
        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .satisfies(rootNode -> assertThat(rootNode.get("children"))
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                        .singleElement()
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .satisfies(unit -> assertThat(unit.get("positions"))
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                .singleElement()
                                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                .satisfies(positionNode -> assertThat(positionNode.get("users"))
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                        .singleElement()
                                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                        .containsEntry("username", "fin.assigned"))));
    }

    @Test
    void enforcesSinglePrimaryPositionAndDisablesAssignments() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryOrganizationDirectoryRepository organizationRepository = new InMemoryOrganizationDirectoryRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                userRepository,
                new InMemoryAuditRepository(),
                organizationRepository
        );
        Map<String, Object> user = service.addUser(Map.of(
                "username", "primary.user",
                "displayName", "Primary User",
                "roles", List.of("viewer")
        ));
        Map<String, Object> unit = service.createOrganizationUnit(Map.of(
                "code", "OPS",
                "name", "Operations",
                "unitType", "department"
        ));
        Map<String, Object> firstPosition = service.createOrganizationPosition(Map.of(
                "organizationUnitId", unit.get("unitId"),
                "code", "ops_manager",
                "name", "Ops Manager",
                "roles", List.of("ops_manager")
        ));
        Map<String, Object> secondPosition = service.createOrganizationPosition(Map.of(
                "organizationUnitId", unit.get("unitId"),
                "code", "ops_director",
                "name", "Ops Director",
                "roles", List.of("ops_director")
        ));

        Map<String, Object> firstAssignment = service.assignUserToOrganizationPosition(Map.of(
                "userId", user.get("userId"),
                "positionId", firstPosition.get("positionId"),
                "primary", true
        ));
        Map<String, Object> secondAssignment = service.assignUserToOrganizationPosition(Map.of(
                "userId", user.get("userId"),
                "positionId", secondPosition.get("positionId"),
                "primary", true
        ));

        assertThat(organizationRepository.findEnabledPositionAssignments())
                .filteredOn(assignment -> assignment.userId().equals(user.get("userId")) && assignment.primary())
                .singleElement()
                .extracting(OrganizationPositionAssignment::positionId)
                .isEqualTo(secondPosition.get("positionId"));
        assertThat(service.disableOrganizationPositionAssignment(((Number) secondAssignment.get("assignmentId")).longValue(), Map.of("reason", "role ended")))
                .containsEntry("assignmentId", secondAssignment.get("assignmentId"))
                .containsEntry("status", "disabled");
        Map<String, Object> directory = service.organizationDirectory();
        assertThat(directory.get("roles"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .extracting(role -> String.valueOf(((Map<?, ?>) role).get("role")))
                .contains("ops_manager")
                .doesNotContain("ops_director");
        assertThat(directory.get("organizationTree"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .satisfies(unitNode -> {
                    List<?> positions = (List<?>) unitNode.get("positions");
                    assertThat(positions)
                            .filteredOn(position -> "ops_manager".equals(((Map<?, ?>) position).get("code")))
                            .singleElement()
                            .satisfies(position -> assertThat(((Map<?, ?>) position).get("users"))
                                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                    .singleElement()
                                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                                    .containsEntry("displayName", "Primary User"));
                    assertThat(positions)
                            .filteredOn(position -> "ops_director".equals(((Map<?, ?>) position).get("code")))
                            .singleElement()
                            .satisfies(position -> assertThat(((Map<?, ?>) position).get("users"))
                                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                                    .isEmpty());
                });
        assertThat(organizationRepository.findEnabledPositionAssignments())
                .filteredOn(assignment -> assignment.userId().equals(user.get("userId")))
                .singleElement()
                .extracting(OrganizationPositionAssignment::id)
                .isEqualTo(firstAssignment.get("assignmentId"));
    }

    @Test
    void filtersOrganizationPositionAssignmentsByActiveWindow() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryOrganizationDirectoryRepository organizationRepository = new InMemoryOrganizationDirectoryRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                userRepository,
                new InMemoryAuditRepository(),
                organizationRepository
        );
        Map<String, Object> currentUser = service.addUser(Map.of(
                "username", "current.assignment",
                "displayName", "Current Assignment",
                "roles", List.of("viewer")
        ));
        Map<String, Object> futureUser = service.addUser(Map.of(
                "username", "future.assignment",
                "displayName", "Future Assignment",
                "roles", List.of("viewer")
        ));
        Map<String, Object> expiredUser = service.addUser(Map.of(
                "username", "expired.assignment",
                "displayName", "Expired Assignment",
                "roles", List.of("viewer")
        ));
        Map<String, Object> unit = service.createOrganizationUnit(Map.of(
                "code", "WIN",
                "name", "Windowed Ops",
                "unitType", "department"
        ));
        Map<String, Object> position = service.createOrganizationPosition(Map.of(
                "organizationUnitId", unit.get("unitId"),
                "code", "windowed_reviewer",
                "name", "Windowed Reviewer",
                "roles", List.of("windowed_reviewer")
        ));

        Map<String, Object> currentAssignment = service.assignUserToOrganizationPosition(Map.of(
                "userId", currentUser.get("userId"),
                "positionId", position.get("positionId"),
                "activeFrom", "2026-01-01T00:00:00Z",
                "activeTo", "2026-12-31T23:59:59Z"
        ));
        service.assignUserToOrganizationPosition(Map.of(
                "userId", futureUser.get("userId"),
                "positionId", position.get("positionId"),
                "activeFrom", "2099-01-01T00:00:00Z",
                "activeTo", "2099-12-31T23:59:59Z"
        ));
        service.assignUserToOrganizationPosition(Map.of(
                "userId", expiredUser.get("userId"),
                "positionId", position.get("positionId"),
                "activeFrom", "2020-01-01T00:00:00Z",
                "activeTo", "2020-12-31T23:59:59Z"
        ));

        Map<String, Object> directory = service.organizationDirectory();

        assertThat(OffsetDateTime.parse(String.valueOf(currentAssignment.get("activeFrom"))))
                .isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        assertThat(OffsetDateTime.parse(String.valueOf(currentAssignment.get("activeTo"))))
                .isEqualTo(OffsetDateTime.parse("2026-12-31T23:59:59Z"));
        assertThat(directory.toString())
                .contains("Current Assignment", "windowed_reviewer")
                .doesNotContain("Future Assignment", "Expired Assignment");
    }

    @Test
    void updatesOrganizationPositionAssignmentWindowAndPrimaryFlag() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryOrganizationDirectoryRepository organizationRepository = new InMemoryOrganizationDirectoryRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                userRepository,
                new InMemoryAuditRepository(),
                organizationRepository
        );
        Map<String, Object> user = service.addUser(Map.of(
                "username", "editable.assignment",
                "displayName", "Editable Assignment",
                "roles", List.of("viewer")
        ));
        Map<String, Object> unit = service.createOrganizationUnit(Map.of(
                "code", "EDIT",
                "name", "Editable Ops",
                "unitType", "department"
        ));
        Map<String, Object> firstPosition = service.createOrganizationPosition(Map.of(
                "organizationUnitId", unit.get("unitId"),
                "code", "editable_primary",
                "name", "Editable Primary",
                "roles", List.of("editable_primary")
        ));
        Map<String, Object> secondPosition = service.createOrganizationPosition(Map.of(
                "organizationUnitId", unit.get("unitId"),
                "code", "editable_secondary",
                "name", "Editable Secondary",
                "roles", List.of("editable_secondary")
        ));
        Map<String, Object> firstAssignment = service.assignUserToOrganizationPosition(Map.of(
                "userId", user.get("userId"),
                "positionId", firstPosition.get("positionId"),
                "primary", true,
                "activeFrom", "2026-01-01T00:00:00Z",
                "activeTo", "2026-01-31T23:59:59Z"
        ));
        Map<String, Object> secondAssignment = service.assignUserToOrganizationPosition(Map.of(
                "userId", user.get("userId"),
                "positionId", secondPosition.get("positionId"),
                "primary", false,
                "activeFrom", "2099-01-01T00:00:00Z",
                "activeTo", "2099-12-31T23:59:59Z"
        ));

        Map<String, Object> updated = service.updateOrganizationPositionAssignment(
                ((Number) secondAssignment.get("assignmentId")).longValue(),
                Map.of(
                        "primary", true,
                        "activeFrom", "2026-01-01T00:00:00Z",
                        "activeTo", "2026-12-31T23:59:59Z"
                )
        );
        Map<String, Object> directory = service.organizationDirectory();

        assertThat(updated)
                .containsEntry("assignmentId", secondAssignment.get("assignmentId"))
                .containsEntry("userId", user.get("userId"))
                .containsEntry("positionId", secondPosition.get("positionId"))
                .containsEntry("primary", true)
                .containsEntry("status", "enabled");
        assertThat(OffsetDateTime.parse(String.valueOf(updated.get("activeFrom"))))
                .isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        assertThat(OffsetDateTime.parse(String.valueOf(updated.get("activeTo"))))
                .isEqualTo(OffsetDateTime.parse("2026-12-31T23:59:59Z"));
        assertThat(organizationRepository.findPositionAssignmentById(((Number) firstAssignment.get("assignmentId")).longValue()))
                .get()
                .extracting(OrganizationPositionAssignment::primary)
                .isEqualTo(false);
        assertThat(directory.get("departments").toString())
                .contains("Editable Assignment", "Editable Secondary")
                .doesNotContain("Editable Primary");
        assertThat(directory.get("roles").toString())
                .contains("editable_secondary")
                .doesNotContain("editable_primary");
    }

    @Test
    void batchImportsOrganizationPositionAssignmentsWithRowResults() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryOrganizationDirectoryRepository organizationRepository = new InMemoryOrganizationDirectoryRepository();
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                userRepository,
                new InMemoryAuditRepository(),
                organizationRepository
        );
        Map<String, Object> user = service.addUser(Map.of(
                "username", "batch.assignment",
                "displayName", "Batch Assignment",
                "roles", List.of("viewer")
        ));
        Map<String, Object> unit = service.createOrganizationUnit(Map.of(
                "code", "BATCH",
                "name", "Batch Ops",
                "unitType", "department"
        ));
        Map<String, Object> firstPosition = service.createOrganizationPosition(Map.of(
                "organizationUnitId", unit.get("unitId"),
                "code", "batch_primary",
                "name", "Batch Primary",
                "roles", List.of("batch_primary")
        ));
        Map<String, Object> secondPosition = service.createOrganizationPosition(Map.of(
                "organizationUnitId", unit.get("unitId"),
                "code", "batch_secondary",
                "name", "Batch Secondary",
                "roles", List.of("batch_secondary")
        ));

        Map<String, Object> result = service.batchImportOrganizationPositionAssignments(Map.of(
                "assignments", List.of(
                        Map.of(
                                "userId", user.get("userId"),
                                "positionId", firstPosition.get("positionId"),
                                "primary", true,
                                "activeFrom", "2026-01-01T00:00:00Z",
                                "activeTo", "2026-01-31T23:59:59Z"
                        ),
                        Map.of(
                                "userId", user.get("userId"),
                                "positionId", secondPosition.get("positionId"),
                                "primary", true,
                                "activeFrom", "2026-01-01T00:00:00Z",
                                "activeTo", "2026-12-31T23:59:59Z"
                        ),
                        Map.of(
                                "userId", 9999L,
                                "positionId", firstPosition.get("positionId")
                        ),
                        Map.of(
                                "userId", user.get("userId"),
                                "positionId", 9999L
                        )
                )
        ));
        Map<String, Object> directory = service.organizationDirectory();

        assertThat(result)
                .containsEntry("imported", 2)
                .containsEntry("failed", 2);
        assertThat(result.get("items"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .hasSize(4)
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("status", "imported")
                        .containsEntry("positionId", firstPosition.get("positionId")))
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("status", "imported")
                        .containsEntry("positionId", secondPosition.get("positionId"))
                        .containsEntry("primary", true))
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("status", "failed")
                        .containsEntry("reason", "user_not_found"))
                .anySatisfy(item -> assertThat(item)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("status", "failed")
                        .containsEntry("reason", "position_not_found"));
        assertThat(organizationRepository.findEnabledPositionAssignments())
                .filteredOn(assignment -> assignment.userId().equals(user.get("userId")) && assignment.primary())
                .singleElement()
                .extracting(OrganizationPositionAssignment::positionId)
                .isEqualTo(secondPosition.get("positionId"));
        assertThat(directory.get("roles").toString())
                .contains("batch_secondary")
                .doesNotContain("batch_primary");
    }

    @Test
    void permissionMatrixContainsRolePermissionMappings() {
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                new InMemoryUserRepository()
        );

        Map<String, Object> matrix = service.permissionMatrix();

        assertThat(matrix.get("roles")).asList().contains("system_admin", "analyst", "viewer");
        assertThat(matrix.get("permissions")).asList().contains(
                "report:create",
                "report:template:manage",
                "collaboration:write",
                "knowledge:upload",
                "datasource:manage",
                "audit:read",
                "dashboard:read",
                "notification:read",
                "user:manage"
        );
        assertThat(matrix.get("rolePermissions"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKey("system_admin")
                .containsKey("analyst")
                .satisfies(rolePermissions -> {
                    assertThat(rolePermissions.get("system_admin")).asList().contains("report:template:manage", "collaboration:write", "datasource:manage", "audit:read", "dashboard:read", "notification:read");
                    assertThat(rolePermissions.get("senior_analyst")).asList().contains("report:export", "report:template:manage", "collaboration:write");
                    assertThat(rolePermissions.get("analyst")).asList().contains("report:export", "collaboration:write", "dashboard:read", "notification:read").doesNotContain("report:template:manage", "audit:read");
                    assertThat(rolePermissions.get("viewer")).asList().doesNotContain("collaboration:write");
                });
    }

    @Test
    void permissionMatrixContainsEveryControllerRequiredPermission() throws IOException {
        PermissionApplicationService service = new PermissionApplicationService(
                new InMemoryShareLinkRepository(),
                new InMemoryReportRepository(),
                new InMemoryUserRepository()
        );

        Set<String> controllerPermissions = controllerRequiredPermissions();
        Map<String, Object> matrix = service.permissionMatrix();

        assertThat(matrix.get("permissions"))
                .asList()
                .containsAll(controllerPermissions);
    }

    @Test
    void createsAndAccessesPersistentShareLinkInsteadOfDemoToken() {
        InMemoryShareLinkRepository repository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        reportRepository.save(new Report(88L, "Shareable report", 501L, ReportStatus.COMPLETED, 1L));
        PermissionApplicationService service = new PermissionApplicationService(repository, reportRepository);

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(88L, Map.of("expiresAt", OffsetDateTime.now().plusDays(1).toString()));
            String shareToken = String.valueOf(created.get("shareToken"));
            Map<String, Object> accessed = service.accessShare(shareToken, Map.of("visitor", "external@example.com"));

            assertThat(created)
                    .containsEntry("reportId", 88L)
                    .containsEntry("createdBy", 501L)
                    .containsEntry("status", "active")
                    .containsKey("shareLinkId")
                    .containsKey("shareToken")
                    .containsKey("shareUrl");
            assertThat(created.get("shareUrl").toString()).isEqualTo("/share/" + shareToken);
            assertThat(shareToken).isNotEqualTo("demo-share-token-valid");
            assertThat(accessed)
                    .containsEntry("accessGranted", true)
                    .containsEntry("shareToken", shareToken)
                    .containsEntry("reportId", 88L)
                    .containsEntry("status", "active");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void protectsPasswordShareWithHashAndWritesAccessAudit() {
        InMemoryShareLinkRepository repository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(188L, "Password share report", 501L, ReportStatus.COMPLETED, 1L));
        PermissionApplicationService service = new PermissionApplicationService(
                repository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(188L, Map.of(
                    "password", "ExternalPass#1",
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));

            assertThat(repository.findByToken(shareToken)).get()
                    .satisfies(link -> {
                        assertThat(link.passwordHash()).isNotBlank();
                        assertThat(link.passwordHash()).isNotEqualTo("ExternalPass#1");
                    });
            assertThat(created).doesNotContainKey("password").doesNotContainKey("passwordHash");

            assertThatThrownBy(() -> service.accessShare(shareToken, Map.of("password", "wrong", "visitor", "external@example.com")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share password invalid");

            Map<String, Object> accessed = service.accessShare(shareToken, Map.of("password", "ExternalPass#1", "visitor", "external@example.com"));
            Map<String, Object> revoked = service.revokeShare(shareToken);

            assertThat(accessed).containsEntry("accessGranted", true).containsEntry("reportId", 188L);
            assertThat(revoked).containsEntry("status", "revoked");
            assertThatThrownBy(() -> service.accessShare(shareToken, Map.of("password", "ExternalPass#1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("share link inactive");
            assertThat(auditRepository.findPage(1, 10))
                    .extracting(OperationLog::operationType)
                    .contains("share_access_failed", "share_access", "share_revoke");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void returnsReadOnlyReportDetailAfterSharePasswordValidation() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportFileRepository exportFileRepository = new InMemoryReportExportFileRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(288L, "Shared customer report", 501L, ReportStatus.COMPLETED, 9L));
        contentRepository.saveCompletedVersion(288L, 501L, List.of(
                Map.of("heading", "Executive Summary", "content", "Customer-ready summary"),
                Map.of("heading", "Evidence", "content", "Cited source material")
        ));
        exportFileRepository.save(Map.of(
                "exportFileId", 8001L,
                "reportId", 288L,
                "status", "completed",
                "format", "markdown",
                "fileName", "shared-customer-report.md",
                "contentType", "text/markdown",
                "sizeBytes", 512L,
                "objectKey", "reports/288/exports/8001/shared-customer-report.md"
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository,
                contentRepository,
                exportFileRepository,
                new InMemoryReportExportStorage()
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            Map<String, Object> created = service.createShare(288L, Map.of(
                    "password", "ExternalPass#1",
                    "allowDownload", true,
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            ));
            String shareToken = String.valueOf(created.get("shareToken"));

            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of("password", "wrong")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share password invalid");

            Map<String, Object> sharedReport = service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            ));

            assertThat(sharedReport)
                    .containsEntry("accessGranted", true)
                    .containsEntry("shareToken", shareToken)
                    .containsEntry("reportId", 288L)
                    .containsEntry("title", "Shared customer report")
                    .containsEntry("status", "completed")
                    .containsEntry("currentVersionId", 9L);
            assertThat(sharedReport).doesNotContainKeys("ownerUserId", "password", "passwordHash");
            assertThat(sharedReport.get("sections"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .hasSize(2)
                    .first()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("heading", "Executive Summary")
                    .containsEntry("content", "Customer-ready summary");
            assertThat(sharedReport.get("exports"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("exportFileId", 8001L)
                    .containsEntry("fileName", "shared-customer-report.md")
                    .containsEntry("format", "markdown");
            assertThat(auditRepository.findPage(1, 10))
                    .extracting(OperationLog::operationType)
                    .contains("share_access_failed", "share_report_view");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void rateLimitsRepeatedInvalidSharePasswordAttemptsByVisitor() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(290L, "Rate limited share report", 501L, ReportStatus.COMPLETED, 9L));
        contentRepository.saveCompletedVersion(290L, 501L, List.of(
                Map.of("heading", "Executive Summary", "content", "Sensitive shared content")
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository,
                contentRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            String shareToken = String.valueOf(service.createShare(290L, Map.of(
                    "password", "ExternalPass#1",
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));
            CurrentUserHolder.clear();

            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                        "password", "wrong",
                        "visitor", "external@example.com"
                )))
                        .isInstanceOf(SecurityException.class)
                        .hasMessageContaining("share password invalid");
            }

            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access rate limited");
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "another@example.com"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", 290L);

            OperationLog rateLimited = auditRepository.findPage(1, 20).stream()
                    .filter(log -> "share_access_rate_limited".equals(log.operationType()))
                    .findFirst()
                    .orElseThrow();
            assertThat(rateLimited.result()).isEqualTo("failed");
            assertThat(rateLimited.actorUserId()).isEqualTo(501L);
            assertThat(rateLimited.detail())
                    .containsEntry("visitor", "external@example.com")
                    .containsEntry("reason", "too_many_invalid_password_attempts");
            assertThat(((Number) rateLimited.detail().get("failedAttempts")).longValue()).isEqualTo(5L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void requiresChallengeAfterRepeatedInvalidSharePasswordAttemptsByVisitor() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(294L, "Challenge protected share report", 501L, ReportStatus.COMPLETED, 9L));
        contentRepository.saveCompletedVersion(294L, 501L, List.of(
                Map.of("heading", "Executive Summary", "content", "Challenge protected content")
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository,
                contentRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            String shareToken = String.valueOf(service.createShare(294L, Map.of(
                    "password", "ExternalPass#1",
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));
            CurrentUserHolder.clear();

            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                        "password", "wrong",
                        "visitor", "external@example.com"
                )))
                        .isInstanceOf(SecurityException.class)
                        .hasMessageContaining("share password invalid");
            }

            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access challenge required");
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com",
                    "challengeAnswer", "WRONG"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access challenge required");
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com",
                    "challengeAnswer", "REPORT"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", 294L);

            assertThat(auditRepository.findPage(1, 20))
                    .filteredOn(log -> "share_access_challenge_required".equals(log.operationType()))
                    .hasSize(2)
                    .allSatisfy(log -> {
                        assertThat(log.result()).isEqualTo("failed");
                        assertThat(log.detail())
                                .containsEntry("reason", "challenge_required")
                                .containsEntry("challengeType", "text")
                                .containsEntry("challengePrompt", "Type REPORT to continue")
                                .containsEntry("visitor", "external@example.com")
                                .containsEntry("failedAttempts", 3L);
                    });
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void rateLimitsRepeatedInvalidSharePasswordAttemptsByRiskFingerprint() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(291L, "Risk aware share report", 501L, ReportStatus.COMPLETED, 9L));
        contentRepository.saveCompletedVersion(291L, 501L, List.of(
                Map.of("heading", "Executive Summary", "content", "Sensitive shared content")
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository,
                contentRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            String shareToken = String.valueOf(service.createShare(291L, Map.of(
                    "password", "ExternalPass#1",
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));
            CurrentUserHolder.clear();

            for (int i = 0; i < 5; i++) {
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("password", "wrong");
                request.put("visitor", "external-" + i + "@example.com");
                request.put("clientIp", "203.0.113.10");
                request.put("userAgent", "Mozilla/5.0 Risk Browser");

                assertThatThrownBy(() -> service.sharedReport(shareToken, request))
                        .isInstanceOf(SecurityException.class)
                        .hasMessageContaining("share password invalid");
            }

            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "new-external@example.com",
                    "clientIp", "203.0.113.10",
                    "userAgent", "Mozilla/5.0 Risk Browser"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access rate limited");
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "new-external@example.com",
                    "clientIp", "203.0.113.11",
                    "userAgent", "Mozilla/5.0 Risk Browser"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", 291L);

            OperationLog failed = auditRepository.findPage(1, 20).stream()
                    .filter(log -> "share_access_failed".equals(log.operationType()))
                    .findFirst()
                    .orElseThrow();
            assertThat(failed.detail())
                    .containsEntry("clientIp", "203.0.113.10")
                    .containsEntry("userAgent", "Mozilla/5.0 Risk Browser")
                    .containsKey("riskFingerprint");

            OperationLog rateLimited = auditRepository.findPage(1, 20).stream()
                    .filter(log -> "share_access_rate_limited".equals(log.operationType()))
                    .findFirst()
                    .orElseThrow();
            assertThat(rateLimited.detail())
                    .containsEntry("clientIp", "203.0.113.10")
                    .containsEntry("userAgent", "Mozilla/5.0 Risk Browser")
                    .containsEntry("reason", "too_many_invalid_password_attempts")
                    .containsKey("riskFingerprint");
            assertThat(((Number) rateLimited.detail().get("failedAttempts")).longValue()).isEqualTo(5L);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void omitsExportListWhenShareDownloadIsNotAllowed() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryReportExportFileRepository exportFileRepository = new InMemoryReportExportFileRepository();
        reportRepository.save(new Report(289L, "Read only share report", 501L, ReportStatus.COMPLETED, 9L));
        exportFileRepository.save(Map.of(
                "exportFileId", 8002L,
                "reportId", 289L,
                "status", "completed",
                "format", "markdown",
                "fileName", "read-only-share.md",
                "contentType", "text/markdown",
                "sizeBytes", 256L,
                "objectKey", "reports/289/exports/8002/read-only-share.md"
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                new InMemoryAuditRepository(),
                contentRepository,
                exportFileRepository,
                new InMemoryReportExportStorage()
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            String shareToken = String.valueOf(service.createShare(289L, Map.of(
                    "password", "ExternalPass#1",
                    "allowDownload", false,
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));

            Map<String, Object> sharedReport = service.sharedReport(shareToken, Map.of("password", "ExternalPass#1"));

            assertThat(sharedReport)
                    .containsEntry("allowDownload", false)
                    .containsEntry("exports", List.of());
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void shareDownloadRequiresExplicitGrantAndValidPassword() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportExportFileRepository exportFileRepository = new InMemoryReportExportFileRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(388L, "Downloadable shared report", 501L, ReportStatus.COMPLETED, 9L));
        exportFileRepository.save(Map.of(
                "exportFileId", 9001L,
                "reportId", 388L,
                "fileName", "report-388.md",
                "contentType", "text/markdown",
                "sizeBytes", 128L,
                "objectKey", "reports/388/exports/9001/report-388.md"
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository,
                new InMemoryReportContentRepository(),
                exportFileRepository,
                new InMemoryReportExportStorage()
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            String deniedToken = String.valueOf(service.createShare(388L, Map.of(
                    "password", "ExternalPass#1",
                    "allowDownload", false,
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));
            String allowedToken = String.valueOf(service.createShare(388L, Map.of(
                    "password", "ExternalPass#1",
                    "allowDownload", true,
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));

            assertThatThrownBy(() -> service.sharedExportDownloadUrl(deniedToken, 9001L, Map.of("password", "ExternalPass#1")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share download access denied");
            assertThatThrownBy(() -> service.sharedExportDownloadUrl(allowedToken, 9001L, Map.of("password", "wrong")))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share password invalid");

            Map<String, Object> download = service.sharedExportDownloadUrl(allowedToken, 9001L, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            ));

            assertThat(download)
                    .containsEntry("exportFileId", 9001L)
                    .containsEntry("reportId", 388L)
                    .containsEntry("fileName", "report-388.md")
                    .containsEntry("downloadPolicy", "share_presigned_url");
            assertThat(download.get("downloadUrl").toString()).startsWith("https://minio.local/");
            assertThat(auditRepository.findPage(1, 10))
                    .extracting(OperationLog::operationType)
                    .contains("share_download_denied", "share_access_failed", "share_export_download");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void shareDownloadCanBeLimitedToAllowedExportFormats() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportExportFileRepository exportFileRepository = new InMemoryReportExportFileRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(390L, "Scoped downloadable shared report", 501L, ReportStatus.COMPLETED, 9L));
        exportFileRepository.save(Map.of(
                "exportFileId", 9003L,
                "reportId", 390L,
                "status", "completed",
                "format", "markdown",
                "fileName", "report-390.md",
                "contentType", "text/markdown",
                "sizeBytes", 128L,
                "objectKey", "reports/390/exports/9003/report-390.md"
        ));
        exportFileRepository.save(Map.of(
                "exportFileId", 9004L,
                "reportId", 390L,
                "status", "completed",
                "format", "pdf",
                "fileName", "report-390.pdf",
                "contentType", "application/pdf",
                "sizeBytes", 256L,
                "objectKey", "reports/390/exports/9004/report-390.pdf"
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository,
                new InMemoryReportContentRepository(),
                exportFileRepository,
                new InMemoryReportExportStorage()
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            String shareToken = String.valueOf(service.createShare(390L, Map.of(
                    "password", "ExternalPass#1",
                    "allowDownload", true,
                    "allowedDownloadFormats", List.of("markdown"),
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));

            Map<String, Object> sharedReport = service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            ));

            assertThat(sharedReport.get("exports"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .singleElement()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("exportFileId", 9003L)
                    .containsEntry("format", "markdown");
            assertThatThrownBy(() -> service.sharedExportDownloadUrl(shareToken, 9004L, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share download format access denied");
            assertThat(service.sharedExportDownloadUrl(shareToken, 9003L, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .containsEntry("exportFileId", 9003L)
                    .containsEntry("downloadPolicy", "share_presigned_url");
            assertThat(auditRepository.findPage(1, 20))
                    .filteredOn(log -> "share_download_denied".equals(log.operationType()))
                    .singleElement()
                    .satisfies(log -> assertThat(log.detail())
                            .containsEntry("reason", "download_format_not_allowed")
                            .containsEntry("exportFileId", 9004L)
                            .containsEntry("format", "pdf"));
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void shareAccessCanBeLimitedByMaximumViewCount() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(391L, "Limited view shared report", 501L, ReportStatus.COMPLETED, 9L));
        contentRepository.saveCompletedVersion(391L, 501L, List.of(
                Map.of("heading", "Executive Summary", "content", "One-time shared content")
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository,
                contentRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            String shareToken = String.valueOf(service.createShare(391L, Map.of(
                    "password", "ExternalPass#1",
                    "maxAccessCount", 1,
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));

            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", 391L);
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access count exceeded");

            assertThat(auditRepository.findPage(1, 20))
                    .filteredOn(log -> "share_access_limit_exceeded".equals(log.operationType()))
                    .singleElement()
                    .satisfies(log -> assertThat(log.detail())
                            .containsEntry("reason", "max_access_count_exceeded")
                            .containsEntry("maxAccessCount", 1)
                            .containsEntry("accessCount", 1L));
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void singleUseShareCanOnlyBeViewedOnce() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(393L, "Single use shared report", 501L, ReportStatus.COMPLETED, 9L));
        contentRepository.saveCompletedVersion(393L, 501L, List.of(
                Map.of("heading", "Executive Summary", "content", "Single-use shared content")
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository,
                contentRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            String shareToken = String.valueOf(service.createShare(393L, Map.of(
                    "password", "ExternalPass#1",
                    "singleUse", true,
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));

            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", 393L);
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share single use already consumed");

            assertThat(auditRepository.findPage(1, 20))
                    .filteredOn(log -> "share_access_single_use_consumed".equals(log.operationType()))
                    .singleElement()
                    .satisfies(log -> assertThat(log.detail())
                            .containsEntry("reason", "single_use_consumed")
                            .containsEntry("visitor", "external@example.com")
                            .containsEntry("accessCount", 1L));
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void shareAccessCanBeScopedToAllowedVisitorsAndDomains() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        InMemoryReportContentRepository contentRepository = new InMemoryReportContentRepository();
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        reportRepository.save(new Report(392L, "Visitor scoped shared report", 501L, ReportStatus.COMPLETED, 9L));
        contentRepository.saveCompletedVersion(392L, 501L, List.of(
                Map.of("heading", "Executive Summary", "content", "Scoped shared content")
        ));
        PermissionApplicationService service = new PermissionApplicationService(
                shareLinkRepository,
                reportRepository,
                new InMemoryUserRepository(),
                auditRepository,
                contentRepository
        );

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));
            String shareToken = String.valueOf(service.createShare(392L, Map.of(
                    "password", "ExternalPass#1",
                    "allowedVisitors", List.of("external@example.com"),
                    "allowedVisitorDomains", List.of("partner.com"),
                    "expiresAt", OffsetDateTime.now().plusDays(1).toString()
            )).get("shareToken"));

            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "external@example.com"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", 392L);
            assertThat(service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "reviewer@partner.com"
            )))
                    .containsEntry("accessGranted", true)
                    .containsEntry("reportId", 392L);
            assertThatThrownBy(() -> service.sharedReport(shareToken, Map.of(
                    "password", "ExternalPass#1",
                    "visitor", "intruder@evil.com"
            )))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("share access scope denied");

            assertThat(auditRepository.findPage(1, 20))
                    .filteredOn(log -> "share_access_scope_denied".equals(log.operationType()))
                    .singleElement()
                    .satisfies(log -> assertThat(log.detail())
                            .containsEntry("reason", "visitor_not_allowed")
                            .containsEntry("visitor", "intruder@evil.com"));
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void shareResponseExposesDownloadPermissionWithoutLeakingHash() {
        InMemoryShareLinkRepository repository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        reportRepository.save(new Report(389L, "Download permission report", 501L, ReportStatus.COMPLETED, 9L));
        PermissionApplicationService service = new PermissionApplicationService(repository, reportRepository);

        try {
            CurrentUserHolder.set(new CurrentUser(501L, Set.of("analyst"), Set.of("report:share")));

            Map<String, Object> created = service.createShare(389L, Map.of("allowDownload", true));

            assertThat(created)
                    .containsEntry("allowDownload", true)
                    .doesNotContainKeys("password", "passwordHash");
            assertThat(repository.findByToken(String.valueOf(created.get("shareToken"))))
                    .get()
                    .extracting(ShareLink::allowDownload)
                    .isEqualTo(true);
        } finally {
            CurrentUserHolder.clear();
        }
    }


    @Test
    void rejectsMissingOrExpiredShareToken() {
        InMemoryShareLinkRepository repository = new InMemoryShareLinkRepository();
        repository.save(ShareLink.newLink(77L, 501L, "expired-token", OffsetDateTime.now().minusDays(1)));
        PermissionApplicationService service = new PermissionApplicationService(repository, new InMemoryReportRepository());

        assertThatThrownBy(() -> service.accessShare("missing-token", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("share link not found");
        assertThatThrownBy(() -> service.accessShare("expired-token", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("share link expired");
    }

    @Test
    void rejectsShareCreationWhenReportBelongsToAnotherUser() {
        InMemoryShareLinkRepository shareLinkRepository = new InMemoryShareLinkRepository();
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        reportRepository.save(new Report(99L, "Private report", 700L, ReportStatus.COMPLETED, 1L));
        PermissionApplicationService service = new PermissionApplicationService(shareLinkRepository, reportRepository);

        try {
            CurrentUserHolder.set(new CurrentUser(701L, Set.of("analyst"), Set.of("report:share")));

            assertThatThrownBy(() -> service.createShare(99L, Map.of()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("report share access denied");
            assertThat(shareLinkRepository.count()).isZero();
        } finally {
            CurrentUserHolder.clear();
        }
    }

    private Set<String> controllerRequiredPermissions() throws IOException {
        Set<String> permissions = new java.util.TreeSet<>();
        try (var files = Files.walk(SOURCE_ROOT.resolve("com/company/report"))) {
            for (Path controller : files
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .toList()) {
                String source = Files.readString(controller);
                Matcher matcher = REQUIRES_PERMISSION.matcher(source);
                while (matcher.find()) {
                    permissions.add(matcher.group(1));
                }
            }
        }
        return permissions;
    }

    private static class InMemoryShareLinkRepository implements ShareLinkRepository {
        private final AtomicLong ids = new AtomicLong(1);
        private final Map<Long, ShareLink> byId = new LinkedHashMap<>();
        private final Map<String, ShareLink> byToken = new LinkedHashMap<>();

        @Override
        public ShareLink save(ShareLink shareLink) {
            ShareLink saved = shareLink.id() == null ? shareLink.withId(ids.getAndIncrement()) : shareLink;
            if (shareLink.id() != null && byId.containsKey(shareLink.id())) {
                saved = shareLink;
            }
            byId.put(saved.id(), saved);
            byToken.put(saved.shareToken(), saved);
            return saved;
        }

        @Override
        public Optional<ShareLink> findByToken(String shareToken) {
            return Optional.ofNullable(byToken.get(shareToken));
        }

        int count() {
            return byId.size();
        }
    }

    private static class InMemoryAuditRepository implements AuditRepository {
        private final AtomicLong ids = new AtomicLong(1);
        private final java.util.List<OperationLog> logs = new java.util.ArrayList<>();

        @Override
        public OperationLog save(OperationLog log) {
            OperationLog saved = log.id() == null ? log.withId(ids.getAndIncrement()) : log;
            logs.add(saved);
            return saved;
        }

        @Override
        public Optional<OperationLog> findById(Long id) {
            return logs.stream().filter(log -> log.id().equals(id)).findFirst();
        }

        @Override
        public java.util.List<OperationLog> findPage(int page, int pageSize) {
            return logs;
        }

        @Override
        public long count() {
            return logs.size();
        }
    }

    private static class InMemoryOrganizationDirectoryRepository implements OrganizationDirectoryRepository {
        private final AtomicLong unitIds = new AtomicLong(1);
        private final AtomicLong positionIds = new AtomicLong(1);
        private final AtomicLong assignmentIds = new AtomicLong(1);
        private final java.util.List<OrganizationUnit> units;
        private final java.util.List<OrganizationPosition> positions;
        private final java.util.List<OrganizationPositionAssignment> assignments = new java.util.ArrayList<>();

        private InMemoryOrganizationDirectoryRepository() {
            this(List.of(), List.of());
        }

        private InMemoryOrganizationDirectoryRepository(List<OrganizationUnit> units, List<OrganizationPosition> positions) {
            this.units = new java.util.ArrayList<>(units);
            this.positions = new java.util.ArrayList<>(positions);
            units.stream().map(OrganizationUnit::id).filter(java.util.Objects::nonNull).mapToLong(Long::longValue).max()
                    .ifPresent(max -> unitIds.set(max + 1));
            positions.stream().map(OrganizationPosition::id).filter(java.util.Objects::nonNull).mapToLong(Long::longValue).max()
                    .ifPresent(max -> positionIds.set(max + 1));
        }

        @Override
        public List<OrganizationUnit> findEnabledUnits() {
            return units.stream()
                    .filter(unit -> "enabled".equals(unit.status()))
                    .toList();
        }

        @Override
        public List<OrganizationPosition> findEnabledPositions() {
            return positions.stream()
                    .filter(position -> "enabled".equals(position.status()))
                    .toList();
        }

        @Override
        public List<OrganizationPositionAssignment> findEnabledPositionAssignments() {
            return assignments.stream()
                    .filter(assignment -> "enabled".equals(assignment.status()))
                    .toList();
        }

        @Override
        public Optional<OrganizationPositionAssignment> findPositionAssignmentById(Long assignmentId) {
            return assignments.stream()
                    .filter(assignment -> assignment.id().equals(assignmentId))
                    .findFirst();
        }

        @Override
        public Optional<OrganizationUnit> findUnitById(Long unitId) {
            return units.stream()
                    .filter(unit -> unit.id().equals(unitId))
                    .findFirst();
        }

        @Override
        public OrganizationUnit saveUnit(OrganizationUnit unit) {
            OrganizationUnit saved = unit.id() == null
                    ? new OrganizationUnit(unitIds.getAndIncrement(), unit.code(), unit.name(), unit.parentId(), unit.unitType(), unit.status(), unit.sortOrder())
                    : unit;
            units.add(saved);
            return saved;
        }

        @Override
        public OrganizationUnit updateUnit(OrganizationUnit unit) {
            for (int index = 0; index < units.size(); index += 1) {
                OrganizationUnit existing = units.get(index);
                if (existing.id().equals(unit.id())) {
                    units.set(index, unit);
                    return unit;
                }
            }
            throw new IllegalArgumentException("organization unit not found: " + unit.id());
        }

        @Override
        public OrganizationPosition savePosition(OrganizationPosition position) {
            OrganizationPosition saved = position.id() == null
                    ? new OrganizationPosition(positionIds.getAndIncrement(), position.organizationUnitId(), position.code(), position.name(), position.roles(), position.managerUserId(), position.status(), position.sortOrder())
                    : position;
            positions.add(saved);
            return saved;
        }

        @Override
        public OrganizationPositionAssignment savePositionAssignment(OrganizationPositionAssignment assignment) {
            if (assignment.primary()) {
                for (int index = 0; index < assignments.size(); index += 1) {
                    OrganizationPositionAssignment existing = assignments.get(index);
                    if (existing.userId().equals(assignment.userId()) && existing.primary() && "enabled".equals(existing.status())) {
                        assignments.set(index, new OrganizationPositionAssignment(
                                existing.id(),
                                existing.userId(),
                                existing.positionId(),
                                false,
                                existing.status(),
                                existing.activeFrom(),
                                existing.activeTo()
                        ));
                    }
                }
            }
            OrganizationPositionAssignment saved = assignment.id() == null
                    ? new OrganizationPositionAssignment(assignmentIds.getAndIncrement(), assignment.userId(), assignment.positionId(), assignment.primary(), assignment.status(), assignment.activeFrom(), assignment.activeTo())
                    : assignment;
            assignments.add(saved);
            return saved;
        }

        @Override
        public OrganizationPositionAssignment disablePositionAssignment(Long assignmentId) {
            for (int index = 0; index < assignments.size(); index += 1) {
                OrganizationPositionAssignment existing = assignments.get(index);
                if (existing.id().equals(assignmentId)) {
                    OrganizationPositionAssignment disabled = new OrganizationPositionAssignment(
                            existing.id(),
                            existing.userId(),
                            existing.positionId(),
                            existing.primary(),
                            "disabled",
                            existing.activeFrom(),
                            existing.activeTo()
                    );
                    assignments.set(index, disabled);
                    return disabled;
                }
            }
            throw new IllegalArgumentException("organization position assignment not found: " + assignmentId);
        }

        @Override
        public OrganizationPositionAssignment updatePositionAssignment(OrganizationPositionAssignment assignment) {
            if (assignment.primary()) {
                for (int index = 0; index < assignments.size(); index += 1) {
                    OrganizationPositionAssignment existing = assignments.get(index);
                    if (existing.userId().equals(assignment.userId())
                            && existing.primary()
                            && "enabled".equals(existing.status())
                            && !existing.id().equals(assignment.id())) {
                        assignments.set(index, new OrganizationPositionAssignment(
                                existing.id(),
                                existing.userId(),
                                existing.positionId(),
                                false,
                                existing.status(),
                                existing.activeFrom(),
                                existing.activeTo()
                        ));
                    }
                }
            }
            for (int index = 0; index < assignments.size(); index += 1) {
                OrganizationPositionAssignment existing = assignments.get(index);
                if (existing.id().equals(assignment.id())) {
                    assignments.set(index, assignment);
                    return assignment;
                }
            }
            throw new IllegalArgumentException("organization position assignment not found: " + assignment.id());
        }
    }

    private static class InMemoryReportContentRepository implements ReportContentRepository {
        private final Map<Long, List<Map<String, Object>>> sectionsByReportId = new LinkedHashMap<>();
        private final AtomicLong versionIds = new AtomicLong(1);

        @Override
        public Long saveCompletedVersion(Long reportId, Long createdBy, List<Map<String, Object>> sections) {
            sectionsByReportId.put(reportId, sections);
            return versionIds.getAndIncrement();
        }

        @Override
        public List<Map<String, Object>> findCurrentSections(Long reportId) {
            return sectionsByReportId.getOrDefault(reportId, List.of());
        }

        @Override
        public List<Map<String, Object>> findSectionsByVersion(Long reportId, Long versionId) {
            return List.of();
        }

        @Override
        public Optional<Map<String, Object>> findReference(Long reportId, Long referenceId) {
            return Optional.empty();
        }

        @Override
        public List<Map<String, Object>> listVersions(Long reportId) {
            return List.of();
        }

        @Override
        public void markCurrentVersion(Long reportId, Long versionId) {
        }

        @Override
        public Long createRollbackVersion(Long reportId, Long sourceVersionId, Long createdBy) {
            return sourceVersionId;
        }
    }

    private static class InMemoryReportExportFileRepository implements ReportExportFileRepository {
        private final Map<Long, Map<String, Object>> files = new LinkedHashMap<>();

        @Override
        public Map<String, Object> save(Map<String, Object> exportFile) {
            files.put(((Number) exportFile.get("exportFileId")).longValue(), new LinkedHashMap<>(exportFile));
            return exportFile;
        }

        @Override
        public Optional<Map<String, Object>> findByReportIdAndExportFileId(Long reportId, Long exportFileId) {
            return findByExportFileId(exportFileId)
                    .filter(file -> reportId.equals(((Number) file.get("reportId")).longValue()));
        }

        @Override
        public Optional<Map<String, Object>> findByExportFileId(Long exportFileId) {
            return Optional.ofNullable(files.get(exportFileId));
        }

        @Override
        public List<Map<String, Object>> findCompletedByReportId(Long reportId) {
            return files.values().stream()
                    .filter(file -> reportId.equals(((Number) file.get("reportId")).longValue()))
                    .filter(file -> "completed".equals(file.getOrDefault("status", "completed")))
                    .map(LinkedHashMap::new)
                    .map(file -> (Map<String, Object>) file)
                    .toList();
        }
    }

    private static class InMemoryReportExportStorage implements ReportExportStorage {
        @Override
        public StoredExport store(String objectKey, String fileName, String contentType, byte[] content) {
            return new StoredExport("report-bucket", objectKey, fileName, contentType, content.length, "https://minio.local/" + objectKey);
        }

        @Override
        public String createDownloadUrl(String objectKey) {
            return "https://minio.local/" + objectKey + "?share=true";
        }
    }

    private static class InMemoryReportRepository implements ReportRepository {
        private final Map<Long, Report> reports = new LinkedHashMap<>();

        @Override
        public Optional<Report> findById(Long id) {
            return Optional.ofNullable(reports.get(id));
        }

        @Override
        public Report save(Report report) {
            reports.put(report.id(), report);
            return report;
        }

        @Override
        public java.util.List<Report> findByOwner(Long ownerUserId, int page, int pageSize) {
            return reports.values().stream()
                    .filter(report -> report.ownerUserId().equals(ownerUserId))
                    .toList();
        }

        @Override
        public long countByOwner(Long ownerUserId) {
            return reports.values().stream()
                    .filter(report -> report.ownerUserId().equals(ownerUserId))
                    .count();
        }
    }
}
