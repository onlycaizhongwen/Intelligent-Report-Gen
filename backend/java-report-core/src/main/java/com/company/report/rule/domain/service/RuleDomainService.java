package com.company.report.rule.domain.service;

import com.company.report.shared.error.BusinessException;
import com.company.report.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class RuleDomainService {
    /** OpenSpec: rule-engine / REQ-RULE-001 / 节点参数缺失 */
    public void ensureDebugInputValid(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            throw new BusinessException(422, ErrorCode.RULE_VALIDATION_FAILED);
        }
    }

    /** OpenSpec: rule-engine / REQ-RULE-001 / 节点 schema 与连线合法性 */
    public void validateDefinition(Map<String, Object> definition) {
        List<Map<String, Object>> nodes = maps(definition.get("nodes"));
        List<Map<String, Object>> edges = maps(definition.get("edges"));
        if (nodes.isEmpty()) {
            throwValidationFailed();
        }
        Set<String> nodeIds = new HashSet<>();
        boolean hasStart = false;
        boolean hasEnd = false;
        for (Map<String, Object> node : nodes) {
            String id = string(node.get("id"));
            String type = string(node.get("type"));
            if (id == null || type == null || !nodeIds.add(id)) {
                throwValidationFailed();
            }
            if ("start".equals(type)) {
                hasStart = true;
            } else if ("end".equals(type)) {
                hasEnd = true;
            } else if ("condition".equals(type)) {
                if (string(node.get("field")) == null || string(node.get("operator")) == null || !node.containsKey("value")) {
                    throwValidationFailed();
                }
            } else if ("branch".equals(type)) {
                if (string(node.get("field")) == null || string(node.get("operator")) == null || !node.containsKey("value")) {
                    throwValidationFailed();
                }
            } else if ("aggregate".equals(type)) {
                String operation = string(node.get("operation"));
                if (string(node.get("sourceField")) == null
                        || string(node.get("outputField")) == null
                        || operation == null
                        || (!"sum".equals(operation) && !"count".equals(operation))) {
                    throwValidationFailed();
                }
                if ("sum".equals(operation) && string(node.get("valueField")) == null) {
                    throwValidationFailed();
                }
            } else if ("action".equals(type)) {
                validateActionNode(node);
            } else if ("approval".equals(type)) {
                String approvalMode = approvalMode(node);
                if (approvalAssigneeRoles(node).isEmpty()
                        || approvalMode == null
                        || string(node.get("approvalTitle")) == null) {
                    throwValidationFailed();
                }
                Integer slaHours = positiveInteger(node.get("slaHours"));
                if (node.containsKey("slaHours") && slaHours == null) {
                    throwValidationFailed();
                }
                if (node.containsKey("slaEscalations") && !validSlaEscalations(node.get("slaEscalations"))) {
                    throwValidationFailed();
                }
                if ((node.containsKey("delegateActiveFrom") && offsetDateTime(node.get("delegateActiveFrom")) == null)
                        || (node.containsKey("delegateActiveTo") && offsetDateTime(node.get("delegateActiveTo")) == null)) {
                    throwValidationFailed();
                }
            } else if ("subprocess".equals(type)) {
                if (positiveInteger(node.get("subprocessRuleId")) == null) {
                    throwValidationFailed();
                }
            } else if (!"action".equals(type)) {
                throwValidationFailed();
            }
        }
        if (!hasStart || !hasEnd) {
            throwValidationFailed();
        }
        for (Map<String, Object> edge : edges) {
            String source = string(edge.get("source"));
            String target = string(edge.get("target"));
            if (source == null || target == null || !nodeIds.contains(source) || !nodeIds.contains(target) || source.equals(target)) {
                throwValidationFailed();
            }
        }
        for (Map<String, Object> node : nodes) {
            if (!"branch".equals(string(node.get("type")))) {
                continue;
            }
            String nodeId = string(node.get("id"));
            boolean hasTruePath = false;
            boolean hasFalsePath = false;
            for (Map<String, Object> edge : edges) {
                if (!nodeId.equals(string(edge.get("source")))) {
                    continue;
                }
                if ("true".equals(string(edge.get("condition")))) {
                    hasTruePath = true;
                }
                if ("false".equals(string(edge.get("condition")))) {
                    hasFalsePath = true;
                }
            }
            if (!hasTruePath || !hasFalsePath) {
                throwValidationFailed();
            }
        }
        validateExecutableTopology(nodes, edges);
    }

    private void validateExecutableTopology(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, String> nodeTypes = new LinkedHashMap<>();
        String startNodeId = null;
        for (Map<String, Object> node : nodes) {
            String nodeId = string(node.get("id"));
            String nodeType = string(node.get("type"));
            nodeTypes.put(nodeId, nodeType);
            if ("start".equals(nodeType) && startNodeId == null) {
                startNodeId = nodeId;
            }
        }
        Map<String, List<Map<String, Object>>> edgesBySource = new LinkedHashMap<>();
        for (Map<String, Object> edge : edges) {
            edgesBySource.computeIfAbsent(string(edge.get("source")), ignored -> new ArrayList<>()).add(edge);
        }
        validatePathReachesEnd(startNodeId, nodeTypes, edgesBySource, new HashSet<>());
    }

    private void validatePathReachesEnd(
            String nodeId,
            Map<String, String> nodeTypes,
            Map<String, List<Map<String, Object>>> edgesBySource,
            Set<String> currentPath
    ) {
        String nodeType = nodeTypes.get(nodeId);
        if (nodeType == null) {
            throwValidationFailed();
        }
        if (!currentPath.add(nodeId)) {
            throwValidationFailed();
        }
        if ("end".equals(nodeType)) {
            currentPath.remove(nodeId);
            return;
        }
        List<Map<String, Object>> outgoingEdges = edgesBySource.getOrDefault(nodeId, List.of());
        if (outgoingEdges.isEmpty()) {
            throwValidationFailed();
        }
        for (Map<String, Object> edge : outgoingEdges) {
            validatePathReachesEnd(string(edge.get("target")), nodeTypes, edgesBySource, currentPath);
        }
        currentPath.remove(nodeId);
    }

    private void validateActionNode(Map<String, Object> node) {
        String actionType = string(node.get("actionType"));
        if (!"create_task".equals(actionType)) {
            return;
        }
        Integer reportId = positiveInteger(node.get("reportId"));
        Integer assigneeUserId = positiveInteger(node.get("assigneeUserId"));
        Map<String, Object> anchor = map(node.get("anchor"));
        Integer startOffset = nonNegativeInteger(anchor.get("startOffset"));
        Integer endOffset = nonNegativeInteger(anchor.get("endOffset"));
        if (reportId == null
                || assigneeUserId == null
                || string(node.get("content")) == null
                || string(anchor.get("sectionId")) == null
                || startOffset == null
                || endOffset == null
                || endOffset <= startOffset
                || string(anchor.get("selectedText")) == null) {
            throwValidationFailed();
        }
    }

    /** OpenSpec: rule-engine / REQ-RULE-001 / 单步调试执行 */
    public Map<String, Object> executeDebug(Map<String, Object> definition, Map<String, Object> input) {
        ensureDebugInputValid(input);
        validateDefinition(definition);
        Map<String, Object> sample = map(input.get("sample"));
        Map<String, Object> context = new LinkedHashMap<>(sample);
        List<Map<String, Object>> nodes = maps(definition.get("nodes"));
        List<Map<String, Object>> edges = maps(definition.get("edges"));
        Map<String, Map<String, Object>> nodesById = nodesById(nodes);
        List<Map<String, Object>> trace = new ArrayList<>();
        boolean matched = true;
        Set<String> visited = new HashSet<>();
        String currentNodeId = startNodeId(nodes);
        while (currentNodeId != null) {
            if (!visited.add(currentNodeId)) {
                throwValidationFailed();
            }
            Map<String, Object> node = nodesById.get(currentNodeId);
            if (node == null) {
                throwValidationFailed();
            }
            String type = string(node.get("type"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nodeId", string(node.get("id")));
            item.put("type", type);
            if ("condition".equals(type)) {
                boolean nodeMatched = evaluateCondition(node, context);
                item.put("matched", nodeMatched);
                matched = matched && nodeMatched;
            } else if ("aggregate".equals(type)) {
                double value = evaluateAggregate(node, context);
                String outputField = string(node.get("outputField"));
                context.put(outputField, value);
                item.put("matched", true);
                item.put("operation", string(node.get("operation")));
                item.put("outputField", outputField);
                item.put("value", value);
            } else if ("branch".equals(type)) {
                boolean nodeMatched = evaluateCondition(node, context);
                String selectedPath = nodeMatched ? "true" : "false";
                String nextNodeId = nextNodeId(edges, currentNodeId, selectedPath);
                item.put("matched", nodeMatched);
                item.put("selectedPath", selectedPath);
                item.put("nextNodeId", nextNodeId);
                trace.add(item);
                currentNodeId = nextNodeId;
                continue;
            } else if ("approval".equals(type)) {
                item.put("matched", true);
                item.put("pendingApproval", true);
                List<String> assigneeRoles = approvalAssigneeRoles(node);
                item.put("assigneeRole", assigneeRoles.isEmpty() ? null : assigneeRoles.get(0));
                item.put("assigneeRoles", assigneeRoles);
                item.put("approvalMode", approvalMode(node));
                item.put("delegateRole", string(node.get("delegateRole")));
                item.put("delegateActiveFrom", string(node.get("delegateActiveFrom")));
                item.put("delegateActiveTo", string(node.get("delegateActiveTo")));
                item.put("approvalTitle", string(node.get("approvalTitle")));
                item.put("slaHours", integer(node.get("slaHours")));
                item.put("slaEscalations", node.get("slaEscalations") instanceof List<?> ? node.get("slaEscalations") : java.util.List.of());
            } else if ("subprocess".equals(type)) {
                item.put("matched", true);
                item.put("subprocessPending", true);
                item.put("subprocessRuleId", integer(node.get("subprocessRuleId")));
                item.put("subprocessName", string(node.get("subprocessName")));
            } else {
                item.put("matched", true);
            }
            trace.add(item);
            if ("end".equals(type)) {
                break;
            }
            currentNodeId = nextNodeId(edges, currentNodeId, null);
        }
        return Map.of(
                "matched", matched,
                "evaluatedNodes", trace.size(),
                "trace", trace
        );
    }

    private boolean evaluateCondition(Map<String, Object> node, Map<String, Object> sample) {
        Object actual = sample.get(string(node.get("field")));
        Object expected = node.get("value");
        String operator = string(node.get("operator"));
        int comparison = compare(actual, expected);
        return switch (operator) {
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case "!=" -> !Objects.equals(String.valueOf(actual), String.valueOf(expected));
            case "=" -> Objects.equals(String.valueOf(actual), String.valueOf(expected));
            case "==" -> Objects.equals(String.valueOf(actual), String.valueOf(expected));
            default -> throwValidationFailed();
        };
    }

    private double evaluateAggregate(Map<String, Object> node, Map<String, Object> context) {
        Object source = context.get(string(node.get("sourceField")));
        if (!(source instanceof List<?>)) {
            throwValidationFailed();
        }
        List<?> rows = (List<?>) source;
        String operation = string(node.get("operation"));
        if ("count".equals(operation)) {
            return rows.size();
        }
        String valueField = string(node.get("valueField"));
        double total = 0d;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?>)) {
                throwValidationFailed();
            }
            Map<?, ?> map = (Map<?, ?>) row;
            Object value = map.get(valueField);
            if (!(value instanceof Number)) {
                throwValidationFailed();
            }
            Number number = (Number) value;
            total += number.doubleValue();
        }
        return total;
    }

    private int compare(Object actual, Object expected) {
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return Double.compare(actualNumber.doubleValue(), expectedNumber.doubleValue());
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (value instanceof List<?> values) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : values) {
                if (!(item instanceof Map<?, ?> map)) {
                    throwValidationFailed();
                    continue;
                }
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((key, entry) -> converted.put(String.valueOf(key), entry));
                result.add(converted);
            }
            return result;
        }
        return List.of();
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> input) {
            Map<String, Object> result = new LinkedHashMap<>();
            input.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private Map<String, Map<String, Object>> nodesById(List<Map<String, Object>> nodes) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            result.put(string(node.get("id")), node);
        }
        return result;
    }

    private String startNodeId(List<Map<String, Object>> nodes) {
        for (Map<String, Object> node : nodes) {
            if ("start".equals(string(node.get("type")))) {
                return string(node.get("id"));
            }
        }
        throwValidationFailed();
        return null;
    }

    private String nextNodeId(List<Map<String, Object>> edges, String source, String condition) {
        for (Map<String, Object> edge : edges) {
            if (!source.equals(string(edge.get("source")))) {
                continue;
            }
            if (condition == null || condition.equals(string(edge.get("condition")))) {
                return string(edge.get("target"));
            }
        }
        return null;
    }

    private String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private List<String> approvalAssigneeRoles(Map<String, Object> node) {
        List<String> roles = new ArrayList<>();
        String legacyRole = string(node.get("assigneeRole"));
        if (legacyRole != null) {
            roles.add(legacyRole);
        }
        Object configuredRoles = node.get("assigneeRoles");
        if (configuredRoles instanceof List<?> values) {
            for (Object value : values) {
                String role = string(value);
                if (role != null && !roles.contains(role)) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    private String approvalMode(Map<String, Object> node) {
        String mode = string(node.get("approvalMode"));
        if (mode == null) {
            return "all";
        }
        return "all".equals(mode) || "any".equals(mode) ? mode : null;
    }

    private boolean validSlaEscalations(Object value) {
        if (!(value instanceof List<?> policies)) {
            return false;
        }
        for (Object item : policies) {
            if (!(item instanceof Map<?, ?> rawPolicy)) {
                return false;
            }
            Integer afterHours = positiveInteger(rawPolicy.get("afterHours"));
            String role = string(rawPolicy.get("role"));
            if (afterHours == null || role == null) {
                return false;
            }
        }
        return true;
    }

    private Integer positiveInteger(Object value) {
        Integer integer = integer(value);
        return integer != null && integer > 0 ? integer : null;
    }

    private Integer nonNegativeInteger(Object value) {
        Integer integer = integer(value);
        return integer != null && integer >= 0 ? integer : null;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            double doubleValue = number.doubleValue();
            int intValue = number.intValue();
            return Double.compare(doubleValue, intValue) == 0 ? intValue : null;
        }
        String text = string(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private java.time.OffsetDateTime offsetDateTime(Object value) {
        String text = string(value);
        if (text == null) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(text);
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private <T> T throwValidationFailed() {
        throw new BusinessException(422, ErrorCode.RULE_VALIDATION_FAILED);
    }
}
