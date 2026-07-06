# Delivery Closure 322: UC-08 Approval Supplement Attachment Configurable Policy

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement attachment policy is now customer-configurable without code changes:

- Spring properties: `rule.approval.supplement-attachment.max-size-bytes` and `rule.approval.supplement-attachment.allowed-content-types`.
- Environment variables: `RULE_APPROVAL_SUPPLEMENT_ATTACHMENT_MAX_SIZE_BYTES` and `RULE_APPROVAL_SUPPLEMENT_ATTACHMENT_ALLOWED_CONTENT_TYPES`.
- Defaults remain the enterprise evidence policy from Closure 321: `10485760` bytes and PDF, JPEG, PNG, CSV, Markdown, plain text, Word, and Excel content types.
- Rejected uploads still fail before object storage writes.
- Rejection audit records include the effective `maxSizeBytes`, `allowedContentTypes`, `contentType`, `sizeBytes`, and `rejectionReason`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#usesConfiguredApprovalSupplementAttachmentMaxSizeBeforeStorageWrite+usesConfiguredApprovalSupplementAttachmentAllowedTypesBeforeStorageWrite" test` first failed because `RuleApprovalSupplementAttachmentPolicy` was not available to service behavior.
- GREEN: the same targeted command passed `2/2` after binding and applying the policy.
- Policy regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenContentTypeIsNotAllowedAndWritesAudit+rejectsApprovalSupplementAttachmentWhenFileIsTooLargeAndWritesAudit+usesConfiguredApprovalSupplementAttachmentMaxSizeBeforeStorageWrite+usesConfiguredApprovalSupplementAttachmentAllowedTypesBeforeStorageWrite" test` passed `4/4`.
- Rule regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest" test` passed `93/93`.
- Contract: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract" test` passed `1/1`.
- Prod profile context: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportCoreProdProfileContextTest" test` passed `1/1`.
- Smoke harness guard: `node --test tests/unit/node/p3_local_smoke_bundle.test.mjs` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApprovalSupplementAttachmentPolicy.java`
- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApplicationConfig.java`
- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApplicationService.java`
- `backend/java-report-core/src/main/resources/application.yml`
- `backend/java-report-core/src/main/resources/application-dev.yml`
- `backend/java-report-core/src/main/resources/application-prod.yml`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- Antivirus scanning and deep content inspection are not implemented in this closure.
- Live browser and Higress upload smoke are rerun in Closure 323 after local Docker services are active.
