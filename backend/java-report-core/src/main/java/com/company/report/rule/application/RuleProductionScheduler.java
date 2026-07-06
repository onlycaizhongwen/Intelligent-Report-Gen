package com.company.report.rule.application;

import com.company.report.rule.domain.model.Rule;
import com.company.report.rule.domain.repository.RuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class RuleProductionScheduler {
    private final RuleRepository ruleRepository;
    private final RuleApplicationService service;
    private final boolean enabled;

    public RuleProductionScheduler(RuleRepository ruleRepository,
                                   RuleApplicationService service,
                                   @Value("${rule.production.scheduler.enabled:false}") boolean enabled) {
        this.ruleRepository = ruleRepository;
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${rule.production.scheduler.fixed-delay-ms:60000}")
    public void runDueRules() {
        if (enabled) {
            runDueRulesOnce();
        }
    }

    public int runDueRulesOnce() {
        int processed = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (Rule rule : ruleRepository.findDueScheduledRules(now, 20)) {
            if (retryLimitReached(rule)) {
                continue;
            }
            if (!ruleRepository.tryAcquireRuleScheduleLease(rule.id(), OffsetDateTime.now().plusMinutes(10))) {
                continue;
            }
            try {
                boolean success = false;
                try {
                    var result = service.runScheduledRule(rule);
                    success = "succeeded".equals(result.get("status"));
                } catch (Exception ignored) {
                    success = false;
                }
                int intervalSeconds = rule.scheduleIntervalSeconds() == null ? 300 : rule.scheduleIntervalSeconds();
                int nextFailureCount = success ? 0 : (rule.failureCount() == null ? 0 : rule.failureCount()) + 1;
                long backoffSeconds = success ? intervalSeconds : Math.min(3600L, intervalSeconds * (long) Math.max(1, nextFailureCount));
                if (!success) {
                    ruleRepository.findRuns(rule.id(), 1, 1).stream()
                            .findFirst()
                            .ifPresent(run -> service.createScheduledFailureAlert(rule, runResponse(run), nextFailureCount));
                }
                ruleRepository.updateScheduleState(rule.id(), OffsetDateTime.now().plusSeconds(backoffSeconds), nextFailureCount);
                processed++;
            } finally {
                ruleRepository.releaseRuleScheduleLease(rule.id());
            }
        }
        return processed;
    }

    private boolean retryLimitReached(Rule rule) {
        int maxRetryCount = rule.maxRetryCount() == null ? 3 : rule.maxRetryCount();
        int failureCount = rule.failureCount() == null ? 0 : rule.failureCount();
        return maxRetryCount >= 0 && failureCount >= maxRetryCount;
    }

    private java.util.Map<String, Object> runResponse(com.company.report.rule.domain.model.RuleDebugRun run) {
        return java.util.Map.of(
                "runId", run.id(),
                "errorMessage", run.errorMessage() == null ? "" : run.errorMessage()
        );
    }
}
