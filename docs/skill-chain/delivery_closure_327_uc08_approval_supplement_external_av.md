# Delivery Closure 327: UC-08 Approval Supplement External AV Boundary

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement attachment inspection now has an optional external antivirus boundary before object storage writes:

- The local inspector still performs size/type, signature, and Office archive checks first.
- When enabled, the external AV scanner streams the attachment to a ClamAV-compatible INSTREAM TCP service.
- Malware responses reject the upload with `rejectionReason=malware_detected_by_external_av`.
- Rejections preserve `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine=clamav_instream`.
- External AV is disabled by default in default/dev/prod configuration and is enabled only with `RULE_APPROVAL_SUPPLEMENT_ATTACHMENT_EXTERNAL_AV_ENABLED=true`.
- Production can point at a service named `clamav`; local development is not forced to deploy a new container.

## Configuration

- `RULE_APPROVAL_SUPPLEMENT_ATTACHMENT_EXTERNAL_AV_ENABLED`
- `RULE_APPROVAL_SUPPLEMENT_ATTACHMENT_EXTERNAL_AV_HOST`
- `RULE_APPROVAL_SUPPLEMENT_ATTACHMENT_EXTERNAL_AV_PORT`
- `RULE_APPROVAL_SUPPLEMENT_ATTACHMENT_EXTERNAL_AV_CONNECT_TIMEOUT_MILLIS`
- `RULE_APPROVAL_SUPPLEMENT_ATTACHMENT_EXTERNAL_AV_READ_TIMEOUT_MILLIS`
- `RULE_APPROVAL_SUPPLEMENT_ATTACHMENT_EXTERNAL_AV_MAX_SCAN_SIZE_BYTES`

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenExternalAntivirusReportsMalwareBeforeStorageWrite" test` first failed because the attachment inspector had no external AV injection point.
- GREEN: the same targeted external AV rejection test passed `1/1`.
- Scanner contract: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=com.company.report.rule.application.ClamAvRuleApprovalSupplementAttachmentScannerTest" test` passed `2/2`.
- Upload security regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#uploadsApprovalSupplementAttachmentToRuleScopedStorageAndWritesAudit+rejectsApprovalSupplementAttachmentWhenContentTypeIsNotAllowedAndWritesAudit+rejectsApprovalSupplementAttachmentWhenFileIsTooLargeAndWritesAudit+usesConfiguredApprovalSupplementAttachmentMaxSizeBeforeStorageWrite+usesConfiguredApprovalSupplementAttachmentAllowedTypesBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenMalwareSignatureIsDetectedBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenDeclaredPdfDoesNotMatchFileSignatureBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenExternalAntivirusReportsMalwareBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsMalwareSignatureBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsMacroPayloadBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenOfficeArchiveExpansionLimitIsExceededBeforeStorageWrite" test` passed `11/11`.
- Full rule regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=com.company.report.rule.application.RuleApplicationServiceTest" test` passed `99/99`.
- Structured contract guard: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract" test` passed `1/1`.
- Prod profile context: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=com.company.report.ReportCoreProdProfileContextTest" test` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/ClamAvRuleApprovalSupplementAttachmentScanner.java`
- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApprovalSupplementAttachmentAntivirusScanner.java`
- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApprovalSupplementAttachmentExternalAvProperties.java`
- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApplicationService.java`
- `backend/java-report-core/src/main/resources/application.yml`
- `backend/java-report-core/src/main/resources/application-dev.yml`
- `backend/java-report-core/src/main/resources/application-prod.yml`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/ClamAvRuleApprovalSupplementAttachmentScannerTest.java`
- `backend/java-report-core/src/test/java/com/company/report/ReportCoreProdProfileContextTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- Live ClamAV container smoke has not been run in this closure because it would add or require a local infrastructure dependency.
- Encrypted archive handling, OCR/image malware inspection, and sandbox/deep parser analysis remain future production hardening.
