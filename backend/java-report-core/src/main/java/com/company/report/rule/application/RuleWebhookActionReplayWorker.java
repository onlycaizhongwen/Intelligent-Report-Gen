package com.company.report.rule.application;

import com.company.report.rule.domain.model.RuleActionExecution;
import com.company.report.rule.domain.repository.RuleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class RuleWebhookActionReplayWorker {
    private final RuleRepository ruleRepository;
    private final RuleApplicationService service;
    private final boolean enabled;
    private final Optional<MeterRegistry> meterRegistry;

    public RuleWebhookActionReplayWorker(RuleRepository ruleRepository,
                                         RuleApplicationService service,
                                         @Value("${rule.webhook-replay.worker.enabled:false}") boolean enabled) {
        this(ruleRepository, service, enabled, null);
    }

    @Autowired
    public RuleWebhookActionReplayWorker(RuleRepository ruleRepository,
                                         RuleApplicationService service,
                                         @Value("${rule.webhook-replay.worker.enabled:false}") boolean enabled,
                                         MeterRegistry meterRegistry) {
        this.ruleRepository = ruleRepository;
        this.service = service;
        this.enabled = enabled;
        this.meterRegistry = Optional.ofNullable(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${rule.webhook-replay.worker.fixed-delay-ms:60000}")
    public void runDueReplays() {
        if (enabled) {
            runDueReplaysOnce();
        }
    }

    public int runDueReplaysOnce() {
        return meterRegistry
                .map(registry -> Timer.builder("rule.webhook.replay.worker.duration")
                        .description("Duration of one webhook replay worker scan")
                        .register(registry)
                        .record(this::runDueReplaysOnceMeasured))
                .orElseGet(this::runDueReplaysOnceMeasured);
    }

    private int runDueReplaysOnceMeasured() {
        recordScan();
        int processed = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (RuleActionExecution execution : ruleRepository.findDueWebhookActionExecutions(now, 20)) {
            int maxAsyncReplayAttempts = maxAsyncReplayAttempts(execution);
            if (maxAsyncReplayAttempts <= 0) {
                recordAction("disabled");
                continue;
            }
            if (!ruleRepository.tryAcquireActionExecutionLease(execution.id(), OffsetDateTime.now().plusMinutes(10))) {
                recordAction("lease_skipped");
                continue;
            }
            try {
                if (asyncReplayAttempts(execution) >= maxAsyncReplayAttempts) {
                    service.exhaustWebhookActionReplay(execution.ruleId(), execution.id(), maxAsyncReplayAttempts);
                    recordAction("exhausted");
                } else {
                    service.retryWebhookActionExecution(execution.ruleId(), execution.id());
                    recordAction("succeeded");
                }
                processed++;
            } catch (RuntimeException ignored) {
                recordAction("failed");
                processed++;
            } finally {
                ruleRepository.releaseActionExecutionLease(execution.id());
            }
        }
        return processed;
    }

    private void recordScan() {
        meterRegistry.ifPresent(registry -> registry.counter("rule.webhook.replay.worker.scans").increment());
    }

    private void recordAction(String result) {
        meterRegistry.ifPresent(registry -> registry.counter("rule.webhook.replay.worker.actions", "result", result).increment());
    }

    private int asyncReplayAttempts(RuleActionExecution execution) {
        Integer attempt = execution.attempt();
        Integer maxRetryCount = execution.maxRetryCount();
        return Math.max(0, (attempt == null ? 0 : attempt) - (maxRetryCount == null ? 0 : maxRetryCount) - 1);
    }

    private int maxAsyncReplayAttempts(RuleActionExecution execution) {
        Object value = execution.metadata().get("maxAsyncReplayAttempts");
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 3;
        }
        return Math.max(0, Integer.parseInt(String.valueOf(value)));
    }
}
