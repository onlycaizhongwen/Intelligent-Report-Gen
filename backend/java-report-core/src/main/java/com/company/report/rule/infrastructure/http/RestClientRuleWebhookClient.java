package com.company.report.rule.infrastructure.http;

import com.company.report.rule.application.RuleWebhookClient;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RestClientRuleWebhookClient implements RuleWebhookClient {
    private final RestClient restClient;

    public RestClientRuleWebhookClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public Map<String, Object> send(String endpoint, String method, Map<String, Object> headers, Map<String, Object> body) {
        HttpMethod httpMethod = HttpMethod.valueOf(method == null || method.isBlank() ? "POST" : method.toUpperCase(java.util.Locale.ROOT));
        var request = restClient.method(httpMethod).uri(endpoint);
        headers.forEach((name, value) -> request.header(name, String.valueOf(value)));
        var response = request.body(body).retrieve().toBodilessEntity();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCode", response.getStatusCode().value());
        result.put("status", response.getStatusCode().toString());
        return result;
    }
}
