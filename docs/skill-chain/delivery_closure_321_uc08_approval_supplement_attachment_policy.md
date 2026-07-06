# Delivery Closure 321: UC-08 Approval Supplement Attachment Policy

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement attachment upload now enforces the Java domain policy before object storage writes:

- Maximum size: `10485760` bytes.
- Allowed evidence content types: PDF, JPEG, PNG, CSV, Markdown, plain text, Word, and Excel.
- Unsupported or oversized files are rejected before MinIO storage is invoked.
- Rejected uploads write `rule_approval_supplement_attachment_rejected` audit evidence with `rejectionReason`.
- Successful uploads retain the existing `rule_approval_supplement_attachment_uploaded` audit path and `minio://...` evidence URL response.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenContentTypeIsNotAllowedAndWritesAudit+rejectsApprovalSupplementAttachmentWhenFileIsTooLargeAndWritesAudit" test` first failed because unsupported and oversized files were accepted.
- GREEN: the same targeted command passed `2/2` after adding service-level policy enforcement.
- Regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest" test` passed `91/91`.
- Contract: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract" test` passed `1/1`.
- Smoke harness: `node --test tests/unit/node/p3_local_smoke_bundle.test.mjs` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApplicationService.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- Customer-specific file policy configuration is not yet externalized.
- Antivirus scanning and deep content inspection are not implemented in this closure.
- Real browser and Higress smoke for the happy path are tracked by the P3 smoke closures.
