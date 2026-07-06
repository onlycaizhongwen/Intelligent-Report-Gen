package com.company.report.permission.application;

import com.company.report.audit.domain.model.OperationLog;
import com.company.report.audit.domain.repository.AuditRepository;
import com.company.report.permission.domain.model.OrganizationPosition;
import com.company.report.permission.domain.model.OrganizationPositionAssignment;
import com.company.report.permission.domain.model.OrganizationUnit;
import com.company.report.permission.domain.model.UserAccount;
import com.company.report.permission.domain.model.ShareLink;
import com.company.report.permission.domain.repository.OrganizationDirectoryRepository;
import com.company.report.permission.domain.repository.ShareLinkRepository;
import com.company.report.permission.domain.repository.UserRepository;
import com.company.report.permission.infrastructure.persistence.InMemoryUserRepository;
import com.company.report.permission.infrastructure.persistence.NoopOrganizationDirectoryRepository;
import com.company.report.report.domain.model.Report;
import com.company.report.report.domain.repository.ReportContentRepository;
import com.company.report.report.domain.repository.ReportExportFileRepository;
import com.company.report.report.domain.repository.ReportExportStorage;
import com.company.report.report.domain.repository.ReportRepository;
import com.company.report.shared.api.PageResponse;
import com.company.report.shared.security.CurrentUser;
import com.company.report.shared.security.CurrentUserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class PermissionApplicationService {
    private static final int SHARE_ACCESS_INVALID_PASSWORD_LIMIT = 5;
    private static final int SHARE_ACCESS_CHALLENGE_THRESHOLD = 3;
    private static final int SHARE_ACCESS_RATE_LIMIT_WINDOW_MINUTES = 15;
    private static final String SHARE_ACCESS_CHALLENGE_ANSWER = "REPORT";
    private static final String SHARE_ACCESS_CHALLENGE_PROMPT = "Type REPORT to continue";

    private final ShareLinkRepository shareLinkRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final ReportContentRepository reportContentRepository;
    private final ReportExportFileRepository reportExportFileRepository;
    private final ReportExportStorage reportExportStorage;
    private final OrganizationDirectoryRepository organizationDirectoryRepository;

    @Autowired
    public PermissionApplicationService(ShareLinkRepository shareLinkRepository,
                                        ReportRepository reportRepository,
                                        UserRepository userRepository,
                                        AuditRepository auditRepository,
                                        ReportContentRepository reportContentRepository,
                                        ReportExportFileRepository reportExportFileRepository,
                                        ReportExportStorage reportExportStorage,
                                        OrganizationDirectoryRepository organizationDirectoryRepository) {
        this.shareLinkRepository = shareLinkRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.reportContentRepository = reportContentRepository;
        this.reportExportFileRepository = reportExportFileRepository;
        this.reportExportStorage = reportExportStorage;
        this.organizationDirectoryRepository = organizationDirectoryRepository;
    }

    public PermissionApplicationService(ShareLinkRepository shareLinkRepository,
                                        ReportRepository reportRepository,
                                        UserRepository userRepository,
                                        AuditRepository auditRepository,
                                        ReportContentRepository reportContentRepository,
                                        ReportExportFileRepository reportExportFileRepository,
                                        ReportExportStorage reportExportStorage) {
        this(shareLinkRepository, reportRepository, userRepository, auditRepository, reportContentRepository,
                reportExportFileRepository, reportExportStorage, new NoopOrganizationDirectoryRepository());
    }

    public PermissionApplicationService(ShareLinkRepository shareLinkRepository,
                                        ReportRepository reportRepository,
                                        UserRepository userRepository,
                                        AuditRepository auditRepository,
                                        OrganizationDirectoryRepository organizationDirectoryRepository) {
        this(shareLinkRepository, reportRepository, userRepository, auditRepository, noopReportContentRepository(),
                noopReportExportFileRepository(), noopReportExportStorage(), organizationDirectoryRepository);
    }

    public PermissionApplicationService(ShareLinkRepository shareLinkRepository,
                                        ReportRepository reportRepository,
                                        UserRepository userRepository,
                                        AuditRepository auditRepository,
                                        ReportContentRepository reportContentRepository) {
        this(shareLinkRepository, reportRepository, userRepository, auditRepository, reportContentRepository,
                noopReportExportFileRepository(), noopReportExportStorage());
    }

    public PermissionApplicationService(ShareLinkRepository shareLinkRepository,
                                        ReportRepository reportRepository,
                                        UserRepository userRepository,
                                        AuditRepository auditRepository) {
        this(shareLinkRepository, reportRepository, userRepository, auditRepository, noopReportContentRepository());
    }

    public PermissionApplicationService(ShareLinkRepository shareLinkRepository, ReportRepository reportRepository, UserRepository userRepository) {
        this(shareLinkRepository, reportRepository, userRepository, noopAuditRepository());
    }

    public PermissionApplicationService(ShareLinkRepository shareLinkRepository, ReportRepository reportRepository) {
        this(shareLinkRepository, reportRepository, new InMemoryUserRepository());
    }

    public PageResponse<Map<String, Object>> users(int page, int pageSize) {
        List<Map<String, Object>> items = userRepository.findPage(page, pageSize).stream()
                .map(this::userResponse)
                .toList();
        return new PageResponse<>(items, page, pageSize, userRepository.count());
    }

    public Map<String, Object> addUser(Map<String, Object> request) {
        String username = string(request == null ? null : request.get("username"));
        if (username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        String displayName = string(request.get("displayName"));
        UserAccount saved = userRepository.save(UserAccount.enabled(
                username,
                displayName.isBlank() ? username : displayName,
                string(request.get("department")),
                string(request.get("position")),
                roles(request.get("roles"))
        ));
        return userResponse(saved);
    }

    public Map<String, Object> batchImportUsers(Map<String, Object> request) {
        List<?> users = request == null || !(request.get("users") instanceof List<?> list) ? List.of() : list;
        List<Map<String, Object>> items = users.stream()
                .map(this::importUser)
                .toList();
        long imported = items.stream().filter(item -> "imported".equals(item.get("status"))).count();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("imported", (int) imported);
        response.put("failed", items.size() - (int) imported);
        response.put("items", items);
        return response;
    }

    public Map<String, Object> updateStatus(Long userId, Map<String, Object> request) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        String status = string(request == null ? null : request.get("status"));
        UserAccount saved = userRepository.save(user.withStatus(status.isBlank() ? "disabled" : status));
        return userResponse(saved);
    }

    public Map<String, Object> permissionMatrix() {
        List<String> permissions = List.of(
                "report:create",
                "report:read",
                "report:export",
                "report:template:manage",
                "report:share",
                "collaboration:write",
                "knowledge:manage",
                "knowledge:upload",
                "datasource:manage",
                "rule:manage",
                "rule:debug",
                "audit:read",
                "dashboard:read",
                "notification:read",
                "user:manage",
                "permission:read"
        );
        return Map.of(
                        "roles", List.of("system_admin", "senior_analyst", "analyst", "viewer"),
                        "permissions", permissions,
                        "rolePermissions", Map.of(
                        "system_admin", permissions,
                        "senior_analyst", List.of("report:create", "report:read", "report:export", "report:template:manage", "report:share", "collaboration:write", "knowledge:manage", "knowledge:upload", "datasource:manage", "rule:manage", "rule:debug", "audit:read", "dashboard:read", "notification:read", "permission:read"),
                        "analyst", List.of("report:create", "report:read", "report:export", "collaboration:write", "knowledge:upload", "rule:debug", "dashboard:read", "notification:read"),
                        "viewer", List.of("report:read")
                )
        );
    }

    public Map<String, Object> createOrganizationUnit(Map<String, Object> request) {
        String code = string(request == null ? null : request.get("code"));
        String name = string(request == null ? null : request.get("name"));
        if (code.isBlank()) {
            throw new IllegalArgumentException("organization unit code is required");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("organization unit name is required");
        }
        OrganizationUnit saved = organizationDirectoryRepository.saveUnit(new OrganizationUnit(
                null,
                code,
                name,
                nullableLong(request.get("parentId")),
                string(request.get("unitType")).isBlank() ? "department" : string(request.get("unitType")),
                "enabled",
                intValue(request.get("sortOrder"))
        ));
        return organizationUnitResponse(saved, Map.of(), Map.of(), Map.of());
    }

    public Map<String, Object> updateOrganizationUnit(Long unitId, Map<String, Object> request) {
        OrganizationUnit existing = organizationDirectoryRepository.findUnitById(unitId)
                .orElseThrow(() -> new IllegalArgumentException("organization unit not found: " + unitId));
        if (!"enabled".equals(existing.status())) {
            throw new IllegalArgumentException("disabled organization unit cannot be updated: " + unitId);
        }
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        String requestedName = safeRequest.containsKey("name") ? string(safeRequest.get("name")) : existing.name();
        if (requestedName.isBlank()) {
            throw new IllegalArgumentException("organization unit name is required");
        }
        Long requestedParentId = safeRequest.containsKey("parentId")
                ? nullableLong(safeRequest.get("parentId"))
                : existing.parentId();
        validateOrganizationUnitParent(existing.id(), requestedParentId);
        OrganizationUnit updated = organizationDirectoryRepository.updateUnit(new OrganizationUnit(
                existing.id(),
                existing.code(),
                requestedName,
                requestedParentId,
                safeRequest.containsKey("unitType") && !string(safeRequest.get("unitType")).isBlank()
                        ? string(safeRequest.get("unitType"))
                        : existing.unitType(),
                existing.status(),
                safeRequest.containsKey("sortOrder") ? intValue(safeRequest.get("sortOrder")) : existing.sortOrder()
        ));
        return organizationUnitResponse(updated, Map.of(), Map.of(), Map.of());
    }

    public Map<String, Object> createOrganizationPosition(Map<String, Object> request) {
        Long organizationUnitId = nullableLong(request == null ? null : request.get("organizationUnitId"));
        String code = string(request == null ? null : request.get("code"));
        String name = string(request == null ? null : request.get("name"));
        if (organizationUnitId == null) {
            throw new IllegalArgumentException("organizationUnitId is required");
        }
        if (code.isBlank()) {
            throw new IllegalArgumentException("organization position code is required");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("organization position name is required");
        }
        OrganizationPosition saved = organizationDirectoryRepository.savePosition(new OrganizationPosition(
                null,
                organizationUnitId,
                code,
                name,
                stringList(request.get("roles")),
                nullableLong(request.get("managerUserId")),
                "enabled",
                intValue(request.get("sortOrder"))
        ));
        return organizationPositionResponse(saved);
    }

    public Map<String, Object> assignUserToOrganizationPosition(Map<String, Object> request) {
        Long userId = nullableLong(request == null ? null : request.get("userId"));
        Long positionId = nullableLong(request == null ? null : request.get("positionId"));
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (positionId == null) {
            throw new IllegalArgumentException("positionId is required");
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        boolean positionExists = organizationDirectoryRepository.findEnabledPositions().stream()
                .anyMatch(position -> position.id().equals(positionId));
        if (!positionExists) {
            throw new IllegalArgumentException("organization position not found: " + positionId);
        }
        OrganizationPositionAssignment saved = organizationDirectoryRepository.savePositionAssignment(new OrganizationPositionAssignment(
                null,
                userId,
                positionId,
                booleanValue(request.get("primary")),
                "enabled",
                nullableOffsetDateTime(request.get("activeFrom")),
                nullableOffsetDateTime(request.get("activeTo"))
        ));
        return organizationPositionAssignmentResponse(saved);
    }

    public Map<String, Object> batchImportOrganizationPositionAssignments(Map<String, Object> request) {
        List<?> assignments = request == null || !(request.get("assignments") instanceof List<?> list) ? List.of() : list;
        List<Map<String, Object>> items = assignments.stream()
                .map(this::importOrganizationPositionAssignment)
                .toList();
        long imported = items.stream().filter(item -> "imported".equals(item.get("status"))).count();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("imported", (int) imported);
        response.put("failed", items.size() - (int) imported);
        response.put("items", items);
        return response;
    }

    public Map<String, Object> disableOrganizationPositionAssignment(Long assignmentId, Map<String, Object> request) {
        OrganizationPositionAssignment assignment = organizationDirectoryRepository.findPositionAssignmentById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("organization position assignment not found: " + assignmentId));
        if (!"enabled".equals(assignment.status())) {
            return organizationPositionAssignmentResponse(assignment);
        }
        return organizationPositionAssignmentResponse(organizationDirectoryRepository.disablePositionAssignment(assignmentId));
    }

    public Map<String, Object> updateOrganizationPositionAssignment(Long assignmentId, Map<String, Object> request) {
        OrganizationPositionAssignment existing = organizationDirectoryRepository.findPositionAssignmentById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("organization position assignment not found: " + assignmentId));
        if (!"enabled".equals(existing.status())) {
            throw new IllegalArgumentException("disabled organization position assignment cannot be updated: " + assignmentId);
        }
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        OrganizationPositionAssignment updated = organizationDirectoryRepository.updatePositionAssignment(new OrganizationPositionAssignment(
                existing.id(),
                existing.userId(),
                existing.positionId(),
                safeRequest.containsKey("primary") ? booleanValue(safeRequest.get("primary")) : existing.primary(),
                existing.status(),
                safeRequest.containsKey("activeFrom") ? nullableOffsetDateTime(safeRequest.get("activeFrom")) : existing.activeFrom(),
                safeRequest.containsKey("activeTo") ? nullableOffsetDateTime(safeRequest.get("activeTo")) : existing.activeTo()
        ));
        return organizationPositionAssignmentResponse(updated);
    }

    public Map<String, Object> organizationDirectory() {
        List<UserAccount> enabledUsers = userRepository.findPage(1, Integer.MAX_VALUE).stream()
                .filter(user -> "enabled".equals(user.status()))
                .toList();
        Map<Long, UserAccount> enabledUsersById = enabledUsers.stream()
                .collect(java.util.stream.Collectors.toMap(
                        UserAccount::id,
                        user -> user,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<OrganizationUnit> enabledUnits = organizationDirectoryRepository.findEnabledUnits();
        Map<Long, OrganizationUnit> unitsById = enabledUnits.stream()
                .collect(java.util.stream.Collectors.toMap(
                        OrganizationUnit::id,
                        unit -> unit,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<OrganizationPosition> enabledPositions = organizationDirectoryRepository.findEnabledPositions();
        Map<Long, OrganizationPosition> positionsById = enabledPositions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        OrganizationPosition::id,
                        position -> position,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        OffsetDateTime now = OffsetDateTime.now();
        List<OrganizationPositionAssignment> enabledAssignments = organizationDirectoryRepository.findEnabledPositionAssignments();
        Set<Long> formallyAssignedUserIds = enabledAssignments.stream()
                .map(OrganizationPositionAssignment::userId)
                .collect(java.util.stream.Collectors.toSet());
        List<OrganizationDirectoryUserView> assignmentViews = enabledAssignments.stream()
                .filter(assignment -> assignmentActiveAt(assignment, now))
                .map(assignment -> organizationDirectoryUserView(assignment, enabledUsersById, positionsById, unitsById))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparing(OrganizationDirectoryUserView::department)
                        .thenComparing(OrganizationDirectoryUserView::position)
                        .thenComparing(view -> view.user().displayName())
                        .thenComparing(view -> view.user().username()))
                .toList();
        Set<Long> assignedUserIds = assignmentViews.stream()
                .map(view -> view.user().id())
                .collect(java.util.stream.Collectors.toSet());
        List<OrganizationDirectoryUserView> fallbackViews = enabledUsers.stream()
                .filter(user -> !assignedUserIds.contains(user.id()))
                .filter(user -> !formallyAssignedUserIds.contains(user.id()))
                .map(user -> new OrganizationDirectoryUserView(
                        user,
                        organizationLabel(user.department(), "No department"),
                        organizationLabel(user.position(), "No position"),
                        user.roles()
                ))
                .sorted(Comparator
                        .comparing(OrganizationDirectoryUserView::department)
                        .thenComparing(OrganizationDirectoryUserView::position)
                        .thenComparing(view -> view.user().displayName())
                        .thenComparing(view -> view.user().username()))
                .toList();
        List<OrganizationDirectoryUserView> directoryUsers = new ArrayList<>();
        directoryUsers.addAll(assignmentViews);
        directoryUsers.addAll(fallbackViews);

        Map<String, Map<String, List<OrganizationDirectoryUserView>>> byDepartment = new TreeMap<>();
        Map<String, List<OrganizationDirectoryUserView>> byRole = new TreeMap<>();
        for (OrganizationDirectoryUserView directoryUser : directoryUsers) {
            byDepartment
                    .computeIfAbsent(directoryUser.department(), ignored -> new TreeMap<>())
                    .computeIfAbsent(directoryUser.position(), ignored -> new ArrayList<>())
                    .add(directoryUser);
            for (String role : directoryUser.roles()) {
                String cleanRole = string(role);
                if (!cleanRole.isBlank()) {
                    byRole.computeIfAbsent(cleanRole, ignored -> new ArrayList<>()).add(directoryUser);
                }
            }
        }

        List<Map<String, Object>> departments = byDepartment.entrySet().stream()
                .map(department -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("department", department.getKey());
                    item.put("positions", department.getValue().entrySet().stream()
                            .map(position -> {
                                List<OrganizationDirectoryUserView> users = sortedDirectoryUsers(position.getValue());
                                Set<String> roles = new TreeSet<>();
                                users.forEach(user -> user.roles().stream()
                                        .map(this::string)
                                        .filter(role -> !role.isBlank())
                                        .forEach(roles::add));
                                Map<String, Object> positionItem = new LinkedHashMap<>();
                                positionItem.put("position", position.getKey());
                                positionItem.put("roles", List.copyOf(roles));
                                positionItem.put("users", users.stream().map(this::organizationUserResponse).toList());
                                return positionItem;
                            })
                            .toList());
                    return item;
                })
                .toList();
        List<Map<String, Object>> roles = byRole.entrySet().stream()
                .map(role -> {
                    List<OrganizationDirectoryUserView> users = sortedDirectoryUsers(role.getValue());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("role", role.getKey());
                    item.put("department", users.stream()
                            .map(OrganizationDirectoryUserView::department)
                            .distinct()
                            .collect(java.util.stream.Collectors.joining(", ")));
                    item.put("position", users.stream()
                            .map(OrganizationDirectoryUserView::position)
                            .distinct()
                            .collect(java.util.stream.Collectors.joining(", ")));
                    item.put("users", users.stream().map(this::organizationUserResponse).toList());
                    return item;
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("departments", departments);
        response.put("roles", roles);
        response.put("organizationTree", organizationTree(assignmentViews));
        return response;
    }

    private List<Map<String, Object>> organizationTree(List<OrganizationDirectoryUserView> assignmentViews) {
        List<OrganizationUnit> units = organizationDirectoryRepository.findEnabledUnits().stream()
                .sorted(Comparator
                        .comparing((OrganizationUnit unit) -> unit.sortOrder())
                        .thenComparing(OrganizationUnit::id))
                .toList();
        if (units.isEmpty()) {
            return List.of();
        }
        Set<Long> enabledUnitIds = units.stream()
                .map(OrganizationUnit::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<Long, List<OrganizationPosition>> positionsByUnit = organizationDirectoryRepository.findEnabledPositions().stream()
                .filter(position -> enabledUnitIds.contains(position.organizationUnitId()))
                .sorted(Comparator
                        .comparing((OrganizationPosition position) -> position.sortOrder())
                        .thenComparing(OrganizationPosition::id))
                .collect(java.util.stream.Collectors.groupingBy(
                        OrganizationPosition::organizationUnitId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Map<Long, List<OrganizationDirectoryUserView>> usersByPosition = assignmentViews.stream()
                .filter(view -> view.positionId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        OrganizationDirectoryUserView::positionId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Map<Long, List<OrganizationUnit>> childrenByParent = units.stream()
                .filter(unit -> unit.parentId() != null && enabledUnitIds.contains(unit.parentId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        OrganizationUnit::parentId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        return units.stream()
                .filter(unit -> unit.parentId() == null || !enabledUnitIds.contains(unit.parentId()))
                .map(unit -> organizationUnitResponse(unit, childrenByParent, positionsByUnit, usersByPosition))
                .toList();
    }

    private Map<String, Object> organizationUnitResponse(OrganizationUnit unit,
                                                         Map<Long, List<OrganizationUnit>> childrenByParent,
                                                         Map<Long, List<OrganizationPosition>> positionsByUnit,
                                                         Map<Long, List<OrganizationDirectoryUserView>> usersByPosition) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("unitId", unit.id());
        response.put("code", unit.code());
        response.put("name", unit.name());
        response.put("parentId", unit.parentId());
        response.put("unitType", unit.unitType());
        response.put("sortOrder", unit.sortOrder());
        response.put("positions", positionsByUnit.getOrDefault(unit.id(), List.of()).stream()
                .map(position -> organizationPositionResponse(position, usersByPosition.getOrDefault(position.id(), List.of())))
                .toList());
        response.put("children", childrenByParent.getOrDefault(unit.id(), List.of()).stream()
                .map(child -> organizationUnitResponse(child, childrenByParent, positionsByUnit, usersByPosition))
                .toList());
        return response;
    }

    private void validateOrganizationUnitParent(Long unitId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (unitId.equals(parentId)) {
            throw new IllegalArgumentException("organization unit parent cannot reference itself: " + unitId);
        }
        List<OrganizationUnit> units = organizationDirectoryRepository.findEnabledUnits();
        Map<Long, OrganizationUnit> unitsById = units.stream()
                .collect(java.util.stream.Collectors.toMap(
                        OrganizationUnit::id,
                        unit -> unit,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (!unitsById.containsKey(parentId)) {
            throw new IllegalArgumentException("organization unit parent not found: " + parentId);
        }
        Long cursor = parentId;
        while (cursor != null) {
            if (unitId.equals(cursor)) {
                throw new IllegalArgumentException("organization unit parent cannot be a descendant: " + parentId);
            }
            OrganizationUnit parent = unitsById.get(cursor);
            cursor = parent == null ? null : parent.parentId();
        }
    }

    private Map<String, Object> organizationPositionResponse(OrganizationPosition position) {
        return organizationPositionResponse(position, List.of());
    }

    private Map<String, Object> organizationPositionResponse(OrganizationPosition position, List<OrganizationDirectoryUserView> users) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("positionId", position.id());
        response.put("organizationUnitId", position.organizationUnitId());
        response.put("code", position.code());
        response.put("name", position.name());
        response.put("roles", position.roles());
        response.put("managerUserId", position.managerUserId());
        response.put("users", sortedDirectoryUsers(users).stream().map(this::organizationUserResponse).toList());
        return response;
    }

    private Map<String, Object> organizationPositionAssignmentResponse(OrganizationPositionAssignment assignment) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("assignmentId", assignment.id());
        response.put("userId", assignment.userId());
        response.put("positionId", assignment.positionId());
        response.put("primary", assignment.primary());
        response.put("status", assignment.status());
        response.put("activeFrom", assignment.activeFrom() == null ? null : assignment.activeFrom().toString());
        response.put("activeTo", assignment.activeTo() == null ? null : assignment.activeTo().toString());
        return response;
    }

    private Map<String, Object> importOrganizationPositionAssignment(Object row) {
        Map<?, ?> source = row instanceof Map<?, ?> map ? map : Map.of();
        Map<String, Object> item = new LinkedHashMap<>();
        Long userId = nullableLong(source.get("userId"));
        Long positionId = nullableLong(source.get("positionId"));
        item.put("userId", userId);
        item.put("positionId", positionId);
        if (userId == null) {
            item.put("status", "failed");
            item.put("reason", "user_id_required");
            return item;
        }
        if (positionId == null) {
            item.put("status", "failed");
            item.put("reason", "position_id_required");
            return item;
        }
        if (userRepository.findById(userId).isEmpty()) {
            item.put("status", "failed");
            item.put("reason", "user_not_found");
            return item;
        }
        boolean positionExists = organizationDirectoryRepository.findEnabledPositions().stream()
                .anyMatch(position -> position.id().equals(positionId));
        if (!positionExists) {
            item.put("status", "failed");
            item.put("reason", "position_not_found");
            return item;
        }
        OrganizationPositionAssignment saved = organizationDirectoryRepository.savePositionAssignment(new OrganizationPositionAssignment(
                null,
                userId,
                positionId,
                booleanValue(source.get("primary")),
                "enabled",
                nullableOffsetDateTime(source.get("activeFrom")),
                nullableOffsetDateTime(source.get("activeTo"))
        ));
        item.putAll(organizationPositionAssignmentResponse(saved));
        item.put("status", "imported");
        return item;
    }

    private boolean assignmentActiveAt(OrganizationPositionAssignment assignment, OffsetDateTime now) {
        if (assignment.activeFrom() != null && assignment.activeFrom().isAfter(now)) {
            return false;
        }
        return assignment.activeTo() == null || !assignment.activeTo().isBefore(now);
    }

    private Optional<OrganizationDirectoryUserView> organizationDirectoryUserView(OrganizationPositionAssignment assignment,
                                                                                  Map<Long, UserAccount> usersById,
                                                                                  Map<Long, OrganizationPosition> positionsById,
                                                                                  Map<Long, OrganizationUnit> unitsById) {
        UserAccount user = usersById.get(assignment.userId());
        OrganizationPosition position = positionsById.get(assignment.positionId());
        if (user == null || position == null) {
            return Optional.empty();
        }
        OrganizationUnit unit = unitsById.get(position.organizationUnitId());
        if (unit == null) {
            return Optional.empty();
        }
        return Optional.of(new OrganizationDirectoryUserView(
                user,
                unit.name(),
                position.name(),
                position.roles().isEmpty() ? user.roles() : position.roles(),
                position.id()
        ));
    }

    public Map<String, Object> createShare(Long reportId, Map<String, Object> request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + reportId));
        if (!report.ownerUserId().equals(currentUserId())) {
            throw new SecurityException("report share access denied: " + reportId);
        }
        OffsetDateTime expiresAt = parseExpiresAt(request == null ? null : request.get("expiresAt"));
        ShareLink saved = shareLinkRepository.save(ShareLink.newLink(
                reportId,
                currentUserId(),
                newShareToken(),
                passwordHash(string(request == null ? null : request.get("password")), reportId),
                booleanValue(request == null ? null : request.get("allowDownload")),
                allowedDownloadFormats(request == null ? null : request.get("allowedDownloadFormats")),
                maxAccessCount(request == null ? null : request.get("maxAccessCount")),
                stringList(request == null ? null : request.get("allowedVisitors")),
                stringList(request == null ? null : request.get("allowedVisitorDomains")),
                booleanValue(request == null ? null : request.get("singleUse")),
                expiresAt
        ));
        return shareLinkResponse(saved, false);
    }

    public Map<String, Object> accessShare(String shareToken, Map<String, Object> request) {
        ShareLink shareLink = shareLinkRepository.findByToken(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("share link not found: " + shareToken));
        ShareAccessRiskContext riskContext = shareAccessRiskContext(request);
        if (!"active".equals(shareLink.status())) {
            writeShareAudit("share_access_failed", shareLink, "failed", riskContext.detail(Map.of("reason", "inactive")));
            throw new IllegalStateException("share link inactive: " + shareToken);
        }
        if (shareLink.expiredAt(OffsetDateTime.now())) {
            writeShareAudit("share_access_failed", shareLink, "failed", riskContext.detail(Map.of("reason", "expired")));
            throw new IllegalStateException("share link expired: " + shareToken);
        }
        String requestPassword = string(request == null ? null : request.get("password"));
        ShareAccessAttemptState attemptState = shareAccessAttemptState(shareLink, riskContext);
        enforceShareAccessRateLimit(shareLink, riskContext, attemptState.failedAttempts());
        if (!passwordMatches(shareLink, requestPassword)) {
            writeShareAudit("share_access_failed", shareLink, "failed", riskContext.detail(Map.of("reason", "invalid_password")));
            throw new SecurityException("share password invalid: " + shareToken);
        }
        enforceShareAccessChallenge(shareLink, request, riskContext, attemptState.failedAttempts());
        writeShareAudit("share_access", shareLink, "succeeded", riskContext.detail(Map.of()));
        return shareLinkResponse(shareLink, true);
    }

    public Map<String, Object> revokeShare(String shareToken) {
        ShareLink shareLink = shareLinkRepository.findByToken(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("share link not found: " + shareToken));
        if (!shareLink.createdBy().equals(currentUserId())) {
            throw new SecurityException("share revoke access denied: " + shareToken);
        }
        ShareLink revoked = shareLinkRepository.save(shareLink.revoke());
        writeShareAudit("share_revoke", revoked, "succeeded", Map.of("shareToken", shareToken));
        return shareLinkResponse(revoked, false);
    }

    public Map<String, Object> sharedReport(String shareToken, Map<String, Object> request) {
        ShareLink shareLink = validateShareAccess(shareToken, request, "share_report_view");
        Report report = reportRepository.findById(shareLink.reportId())
                .orElseThrow(() -> new IllegalArgumentException("report not found: " + shareLink.reportId()));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessGranted", true);
        response.put("shareToken", shareLink.shareToken());
        response.put("reportId", report.id());
        response.put("title", report.title());
        response.put("status", report.status().name().toLowerCase());
        response.put("currentVersionId", report.currentVersionId());
        response.put("sections", reportContentRepository.findCurrentSections(report.id()));
        response.put("allowDownload", shareLink.allowDownload());
        response.put("exports", shareLink.allowDownload() ? sharedExportSummaries(shareLink) : List.of());
        return response;
    }

    public Map<String, Object> sharedExportDownloadUrl(String shareToken, Long exportFileId, Map<String, Object> request) {
        ShareLink shareLink = validateShareAccess(shareToken, request, null);
        if (!shareLink.allowDownload()) {
            writeShareAudit("share_download_denied", shareLink, "failed", Map.of(
                    "reason", "download_not_allowed",
                    "exportFileId", exportFileId
            ));
            throw new SecurityException("share download access denied: " + shareToken);
        }
        Map<String, Object> exportFile = reportExportFileRepository.findByReportIdAndExportFileId(shareLink.reportId(), exportFileId)
                .orElseThrow(() -> new IllegalArgumentException("report export file not found: " + exportFileId));
        String format = string(exportFile.get("format")).toLowerCase();
        if (!shareLink.allowsDownloadFormat(format)) {
            writeShareAudit("share_download_denied", shareLink, "failed", shareAccessRiskContext(request).detail(Map.of(
                    "reason", "download_format_not_allowed",
                    "exportFileId", exportFileId,
                    "format", format
            )));
            throw new SecurityException("share download format access denied: " + shareToken);
        }
        String objectKey = string(exportFile.get("objectKey"));
        String downloadUrl;
        try {
            downloadUrl = reportExportStorage.createDownloadUrl(objectKey);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to create shared report export download URL", ex);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("exportFileId", exportFileId);
        response.put("reportId", exportFile.get("reportId"));
        response.put("fileName", exportFile.get("fileName"));
        response.put("contentType", exportFile.get("contentType"));
        response.put("sizeBytes", exportFile.get("sizeBytes"));
        response.put("downloadPolicy", "share_presigned_url");
        response.put("downloadUrl", downloadUrl);
        response.put("expiresAt", OffsetDateTime.now().plusMinutes(30).toString());
        writeShareAudit("share_export_download", shareLink, "succeeded", shareAccessRiskContext(request).detail(Map.of(
                "exportFileId", exportFileId,
                "downloadPolicy", "share_presigned_url"
        )));
        return response;
    }

    private ShareLink validateShareAccess(String shareToken, Map<String, Object> request, String successOperationType) {
        ShareLink shareLink = shareLinkRepository.findByToken(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("share link not found: " + shareToken));
        ShareAccessRiskContext riskContext = shareAccessRiskContext(request);
        if (!"active".equals(shareLink.status())) {
            writeShareAudit("share_access_failed", shareLink, "failed", riskContext.detail(Map.of("reason", "inactive")));
            throw new IllegalStateException("share link inactive: " + shareToken);
        }
        if (shareLink.expiredAt(OffsetDateTime.now())) {
            writeShareAudit("share_access_failed", shareLink, "failed", riskContext.detail(Map.of("reason", "expired")));
            throw new IllegalStateException("share link expired: " + shareToken);
        }
        String requestPassword = string(request == null ? null : request.get("password"));
        ShareAccessAttemptState attemptState = shareAccessAttemptState(shareLink, riskContext);
        enforceShareAccessRateLimit(shareLink, riskContext, attemptState.failedAttempts());
        if (!passwordMatches(shareLink, requestPassword)) {
            writeShareAudit("share_access_failed", shareLink, "failed", riskContext.detail(Map.of("reason", "invalid_password")));
            throw new SecurityException("share password invalid: " + shareToken);
        }
        enforceShareAccessChallenge(shareLink, request, riskContext, attemptState.failedAttempts());
        if (successOperationType != null && !successOperationType.isBlank()) {
            enforceShareVisitorScope(shareLink, riskContext);
            enforceSingleUseShare(shareLink, riskContext);
            enforceShareAccessCountLimit(shareLink, riskContext);
            writeShareAudit(successOperationType, shareLink, "succeeded", riskContext.detail(Map.of()));
        }
        return shareLink;
    }

    private void enforceSingleUseShare(ShareLink shareLink, ShareAccessRiskContext riskContext) {
        if (!shareLink.singleUse() || shareLink.id() == null) {
            return;
        }
        long accessCount = countSuccessfulShareReportViews(shareLink);
        if (accessCount < 1) {
            return;
        }
        writeShareAudit("share_access_single_use_consumed", shareLink, "failed", riskContext.detail(Map.of(
                "reason", "single_use_consumed",
                "accessCount", accessCount
        )));
        throw new SecurityException("share single use already consumed: " + shareLink.shareToken());
    }

    private void enforceShareVisitorScope(ShareLink shareLink, ShareAccessRiskContext riskContext) {
        if (shareLink.allowsVisitor(riskContext.visitor())) {
            return;
        }
        writeShareAudit("share_access_scope_denied", shareLink, "failed", riskContext.detail(Map.of(
                "reason", "visitor_not_allowed"
        )));
        throw new SecurityException("share access scope denied: " + shareLink.shareToken());
    }

    private void enforceShareAccessCountLimit(ShareLink shareLink, ShareAccessRiskContext riskContext) {
        if (!shareLink.hasAccessCountLimit() || shareLink.id() == null) {
            return;
        }
        long accessCount = countSuccessfulShareReportViews(shareLink);
        if (accessCount < shareLink.maxAccessCount()) {
            return;
        }
        writeShareAudit("share_access_limit_exceeded", shareLink, "failed", riskContext.detail(Map.of(
                "reason", "max_access_count_exceeded",
                "maxAccessCount", shareLink.maxAccessCount(),
                "accessCount", accessCount
        )));
        throw new SecurityException("share access count exceeded: " + shareLink.shareToken());
    }

    private long countSuccessfulShareReportViews(ShareLink shareLink) {
        return auditRepository.countByResourceOperationSince(
                "share_link",
                shareLink.id(),
                "share_report_view",
                null
        );
    }

    private ShareAccessAttemptState shareAccessAttemptState(ShareLink shareLink, ShareAccessRiskContext riskContext) {
        if (shareLink.id() == null) {
            return new ShareAccessAttemptState(0L);
        }
        long failedAttemptsByVisitor = countFailedShareAccess(shareLink, "visitor", riskContext.visitor());
        long failedAttemptsByRisk = countFailedShareAccess(shareLink, "riskFingerprint", riskContext.riskFingerprint());
        long failedAttempts = Math.max(failedAttemptsByVisitor, failedAttemptsByRisk);
        return new ShareAccessAttemptState(failedAttempts);
    }

    private void enforceShareAccessChallenge(ShareLink shareLink,
                                             Map<String, Object> request,
                                             ShareAccessRiskContext riskContext,
                                             long failedAttempts) {
        if (failedAttempts < SHARE_ACCESS_CHALLENGE_THRESHOLD) {
            return;
        }
        String challengeAnswer = string(request == null ? null : request.get("challengeAnswer"));
        if (SHARE_ACCESS_CHALLENGE_ANSWER.equalsIgnoreCase(challengeAnswer)) {
            return;
        }
        writeShareAudit("share_access_challenge_required", shareLink, "failed", riskContext.detail(Map.of(
                "reason", "challenge_required",
                "challengeType", "text",
                "challengePrompt", SHARE_ACCESS_CHALLENGE_PROMPT,
                "failedAttempts", failedAttempts,
                "windowMinutes", SHARE_ACCESS_RATE_LIMIT_WINDOW_MINUTES
        )));
        throw new SecurityException("share access challenge required: " + shareLink.shareToken());
    }

    private void enforceShareAccessRateLimit(ShareLink shareLink, ShareAccessRiskContext riskContext, long failedAttempts) {
        if (failedAttempts < SHARE_ACCESS_INVALID_PASSWORD_LIMIT) {
            return;
        }
        writeShareAudit("share_access_rate_limited", shareLink, "failed", riskContext.detail(Map.of(
                "reason", "too_many_invalid_password_attempts",
                "failedAttempts", failedAttempts,
                "windowMinutes", SHARE_ACCESS_RATE_LIMIT_WINDOW_MINUTES
        )));
        throw new SecurityException("share access rate limited: " + shareLink.shareToken());
    }

    private long countFailedShareAccess(ShareLink shareLink, String detailKey, String detailValue) {
        if (detailValue == null || detailValue.isBlank()) {
            return 0L;
        }
        return auditRepository.countByResourceOperationAndDetailSince(
                "share_link",
                shareLink.id(),
                "share_access_failed",
                detailKey,
                detailValue,
                OffsetDateTime.now().minusMinutes(SHARE_ACCESS_RATE_LIMIT_WINDOW_MINUTES)
        );
    }

    private Map<String, Object> shareLinkResponse(ShareLink shareLink, boolean accessGranted) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (accessGranted) {
            response.put("accessGranted", true);
        }
        response.put("shareLinkId", shareLink.id());
        response.put("reportId", shareLink.reportId());
        response.put("createdBy", shareLink.createdBy());
        response.put("shareToken", shareLink.shareToken());
        response.put("shareUrl", "/share/" + shareLink.shareToken());
        response.put("status", shareLink.status());
        response.put("expiresAt", shareLink.expiresAt() == null ? null : shareLink.expiresAt().toString());
        response.put("passwordRequired", shareLink.passwordHash() != null && !shareLink.passwordHash().isBlank());
        response.put("allowDownload", shareLink.allowDownload());
        response.put("allowedDownloadFormats", shareLink.allowedDownloadFormats() == null ? List.of() : shareLink.allowedDownloadFormats());
        response.put("maxAccessCount", shareLink.maxAccessCount());
        response.put("allowedVisitors", shareLink.allowedVisitors() == null ? List.of() : shareLink.allowedVisitors());
        response.put("allowedVisitorDomains", shareLink.allowedVisitorDomains() == null ? List.of() : shareLink.allowedVisitorDomains());
        response.put("singleUse", shareLink.singleUse());
        return response;
    }

    private List<Map<String, Object>> sharedExportSummaries(ShareLink shareLink) {
        return reportExportFileRepository.findCompletedByReportId(shareLink.reportId()).stream()
                .filter(export -> shareLink.allowsDownloadFormat(string(export.get("format")).toLowerCase()))
                .map(export -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("exportFileId", export.get("exportFileId"));
                    summary.put("fileName", export.get("fileName"));
                    summary.put("format", export.get("format"));
                    summary.put("contentType", export.get("contentType"));
                    summary.put("sizeBytes", export.get("sizeBytes"));
                    summary.put("createdAt", export.get("createdAt"));
                    return summary;
                })
                .toList();
    }

    private OffsetDateTime parseExpiresAt(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return OffsetDateTime.now().plusDays(7);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private String newShareToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String passwordHash(String password, Long reportId) {
        if (password == null || password.isBlank()) {
            return null;
        }
        return sha256(reportId + ":" + password);
    }

    private boolean passwordMatches(ShareLink shareLink, String password) {
        if (shareLink.passwordHash() == null || shareLink.passwordHash().isBlank()) {
            return true;
        }
        return shareLink.passwordHash().equals(passwordHash(password, shareLink.reportId()));
    }

    private ShareAccessRiskContext shareAccessRiskContext(Map<String, Object> request) {
        String visitor = string(request == null ? null : request.get("visitor"));
        String clientIp = string(request == null ? null : request.get("clientIp"));
        String userAgent = string(request == null ? null : request.get("userAgent"));
        String riskFingerprint = clientIp.isBlank() && userAgent.isBlank()
                ? ""
                : sha256(clientIp + "|" + userAgent);
        return new ShareAccessRiskContext(visitor, clientIp, userAgent, riskFingerprint);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to hash share password", ex);
        }
    }

    private void writeShareAudit(String operationType, ShareLink shareLink, String result, Map<String, Object> detail) {
        Map<String, Object> auditDetail = new LinkedHashMap<>(detail == null ? Map.of() : detail);
        auditDetail.put("shareToken", shareLink.shareToken());
        auditDetail.put("reportId", shareLink.reportId());
        auditRepository.save(new OperationLog(
                null,
                shareLink.createdBy(),
                operationType,
                "share_link",
                shareLink.id(),
                result,
                auditDetail,
                OffsetDateTime.now()
        ));
    }

    private Long currentUserId() {
        CurrentUser user = CurrentUserHolder.get();
        return user == null ? 1L : user.userId();
    }

    private Map<String, Object> userResponse(UserAccount user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", user.id());
        response.put("username", user.username());
        response.put("displayName", user.displayName());
        response.put("status", user.status());
        response.put("department", user.department());
        response.put("position", user.position());
        response.put("roles", user.roles());
        return response;
    }

    private Map<String, Object> organizationUserResponse(UserAccount user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", user.id());
        response.put("username", user.username());
        response.put("displayName", user.displayName());
        response.put("roles", user.roles());
        return response;
    }

    private Map<String, Object> organizationUserResponse(OrganizationDirectoryUserView view) {
        Map<String, Object> response = organizationUserResponse(view.user());
        response.put("department", view.department());
        response.put("position", view.position());
        response.put("roles", view.roles());
        return response;
    }

    private List<UserAccount> sortedUsers(List<UserAccount> users) {
        return users.stream()
                .sorted(Comparator
                        .comparing(UserAccount::displayName)
                        .thenComparing(UserAccount::username))
                .toList();
    }

    private List<OrganizationDirectoryUserView> sortedDirectoryUsers(List<OrganizationDirectoryUserView> users) {
        return users.stream()
                .sorted(Comparator
                        .comparing((OrganizationDirectoryUserView view) -> view.user().displayName())
                        .thenComparing(view -> view.user().username()))
                .toList();
    }

    private String organizationLabel(String value, String fallback) {
        String clean = string(value);
        return clean.isBlank() ? fallback : clean;
    }

    private Map<String, Object> importUser(Object rawUser) {
        Map<String, Object> item = new LinkedHashMap<>();
        Map<?, ?> user = rawUser instanceof Map<?, ?> map ? map : Map.of();
        String username = string(user.get("username"));
        item.put("username", username);
        if (username.isBlank()) {
            item.put("status", "failed");
            item.put("reason", "username_required");
            return item;
        }
        if (usernameExists(username)) {
            item.put("status", "failed");
            item.put("reason", "duplicate_username");
            return item;
        }
        String displayName = string(user.get("displayName"));
        UserAccount saved = userRepository.save(UserAccount.enabled(
                username,
                displayName.isBlank() ? username : displayName,
                string(user.get("department")),
                string(user.get("position")),
                roles(user.get("roles"))
        ));
        item.put("userId", saved.id());
        item.put("displayName", saved.displayName());
        item.put("department", saved.department());
        item.put("position", saved.position());
        item.put("roles", saved.roles());
        item.put("status", "imported");
        return item;
    }

    private boolean usernameExists(String username) {
        return userRepository.findPage(1, Integer.MAX_VALUE).stream()
                .anyMatch(user -> user.username().equalsIgnoreCase(username));
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private List<String> roles(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of("viewer");
    }

    private List<String> allowedDownloadFormats(Object value) {
        return stringList(value);
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::string)
                    .filter(format -> !format.isBlank())
                    .map(format -> format.toLowerCase())
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private int maxAccessCount(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(String.valueOf(value).trim()), 0);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("maxAccessCount must be a positive integer", ex);
        }
    }

    private boolean booleanValue(Object value) {
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private Long nullableLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("value must be a number", ex);
        }
    }

    private OffsetDateTime nullableOffsetDateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(String.valueOf(value).trim());
    }

    private int intValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("value must be an integer", ex);
        }
    }

    private record OrganizationDirectoryUserView(
            UserAccount user,
            String department,
            String position,
            List<String> roles,
            Long positionId
    ) {
        private OrganizationDirectoryUserView(UserAccount user, String department, String position, List<String> roles) {
            this(user, department, position, roles, null);
        }
    }

    private record ShareAccessRiskContext(String visitor, String clientIp, String userAgent, String riskFingerprint) {
        private Map<String, Object> detail(Map<String, Object> base) {
            Map<String, Object> detail = new LinkedHashMap<>(base == null ? Map.of() : base);
            if (!visitor.isBlank()) {
                detail.put("visitor", visitor);
            }
            if (!clientIp.isBlank()) {
                detail.put("clientIp", clientIp);
            }
            if (!userAgent.isBlank()) {
                detail.put("userAgent", userAgent);
            }
            if (!riskFingerprint.isBlank()) {
                detail.put("riskFingerprint", riskFingerprint);
            }
            return detail;
        }
    }

    private record ShareAccessAttemptState(long failedAttempts) {
    }

    private static AuditRepository noopAuditRepository() {
        return new AuditRepository() {
            @Override
            public OperationLog save(OperationLog log) {
                return log;
            }

            @Override
            public Optional<OperationLog> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<OperationLog> findPage(int page, int pageSize) {
                return List.of();
            }

            @Override
            public long count() {
                return 0L;
            }
        };
    }

    private static ReportContentRepository noopReportContentRepository() {
        return new ReportContentRepository() {
            @Override
            public Long saveCompletedVersion(Long reportId, Long createdBy, List<Map<String, Object>> sections) {
                return null;
            }

            @Override
            public List<Map<String, Object>> findCurrentSections(Long reportId) {
                return List.of();
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
        };
    }

    private static ReportExportFileRepository noopReportExportFileRepository() {
        return new ReportExportFileRepository() {
            @Override
            public Map<String, Object> save(Map<String, Object> exportFile) {
                return exportFile;
            }

            @Override
            public Optional<Map<String, Object>> findByReportIdAndExportFileId(Long reportId, Long exportFileId) {
                return Optional.empty();
            }

            @Override
            public Optional<Map<String, Object>> findByExportFileId(Long exportFileId) {
                return Optional.empty();
            }

            @Override
            public List<Map<String, Object>> findCompletedByReportId(Long reportId) {
                return List.of();
            }
        };
    }

    private static ReportExportStorage noopReportExportStorage() {
        return new ReportExportStorage() {
            @Override
            public StoredExport store(String objectKey, String fileName, String contentType, byte[] content) {
                return new StoredExport(null, objectKey, fileName, contentType, content == null ? 0L : content.length, null);
            }

            @Override
            public String createDownloadUrl(String objectKey) {
                return "";
            }
        };
    }
}
