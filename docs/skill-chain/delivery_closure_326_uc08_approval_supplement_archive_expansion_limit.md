# Delivery Closure 326: UC-08 Approval Supplement Archive Expansion Limit

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement Office archive inspection now enforces an expansion limit before object storage writes:

- Allowed OOXML Office archive entries are inspected while streaming from the ZIP input.
- If expanded archive content exceeds `10 MiB`, inspection rejects the upload with `rejectionReason=archive_expansion_limit_exceeded`.
- Rejected uploads continue to write `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine`, `inspectionMessage`, `fileName`, `contentType`, and policy fields.
- Rejected archive uploads stop before `DocumentStorage.store(...)`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenOfficeArchiveExpansionLimitIsExceededBeforeStorageWrite" test` first failed because an over-expanded Office archive was accepted.
- GREEN: the same targeted archive expansion test passed `1/1`.
- Upload security regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#uploadsApprovalSupplementAttachmentToRuleScopedStorageAndWritesAudit+rejectsApprovalSupplementAttachmentWhenContentTypeIsNotAllowedAndWritesAudit+rejectsApprovalSupplementAttachmentWhenFileIsTooLargeAndWritesAudit+usesConfiguredApprovalSupplementAttachmentMaxSizeBeforeStorageWrite+usesConfiguredApprovalSupplementAttachmentAllowedTypesBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenMalwareSignatureIsDetectedBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenDeclaredPdfDoesNotMatchFileSignatureBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsMalwareSignatureBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsMacroPayloadBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenOfficeArchiveExpansionLimitIsExceededBeforeStorageWrite" test` passed `10/10`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure is not a full external antivirus deployment.
- Encrypted archive handling, OCR/image malware inspection, and sandbox/deep parser analysis remain future production hardening.
