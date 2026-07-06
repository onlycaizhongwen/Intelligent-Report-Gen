package com.company.report.rule.application;

import com.company.report.rule.domain.repository.RuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RuleApprovalSlaScheduler {
    private final RuleRepository ruleRepository;
    private final RuleApplicationService service;
    private final boolean enabled;

    public RuleApprovalSlaScheduler(RuleRepository ruleRepository,
                                    RuleApplicationService service,
                                    @Value("${rule.approval.sla.scheduler.enabled:false}") boolean enabled) {
        this.ruleRepository = ruleRepository;
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${rule.approval.sla.scheduler.fixed-delay-ms:60000}")
    public void runOverdueApprovals() {
        if (enabled) {
            runOverdueApprovalsOnce();
        }
    }

    public int runOverdueApprovalsOnce() {
        return (int) ruleRepository.findApprovalRecordsByStatus(
                        "pending",
                        RuleRepository.ApprovalRecordFilter.empty(),
                        1,
                        100
                ).stream()
                .filter(service::createApprovalSlaOverdueAlert)
                .count();
    }
}
