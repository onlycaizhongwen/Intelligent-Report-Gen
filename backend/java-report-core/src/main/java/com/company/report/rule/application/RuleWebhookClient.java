package com.company.report.rule.application;

import java.util.Map;

public interface RuleWebhookClient {
    Map<String, Object> send(String endpoint, String method, Map<String, Object> headers, Map<String, Object> body);
}
