package com.company.report.rule.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        RuleApprovalSupplementAttachmentPolicy.class,
        RuleApprovalSupplementAttachmentExternalAvProperties.class
})
class RuleApplicationConfig {
}
