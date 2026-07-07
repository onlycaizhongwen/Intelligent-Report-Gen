# Delivery Closure 331: UC-08 Approval Supplement Office Embedded Objects

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement Office archive inspection now rejects embedded object payloads before object storage:

- The inspector streams allowed OOXML Office archives entry by entry.
- Entries under `/embeddings/`, `/activex/`, or `/controls/` are rejected.
- Typical `oleObject.bin` and `package.bin` entries are rejected.
- Rejections use `rejectionReason=embedded_object_detected`.
- Rejected uploads preserve `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine=basic_attachment_content_inspector`.
- Rejected archives stop before `DocumentStorage.store(...)`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsEmbeddedObjectBeforeStorageWrite" test` first failed because the embedded-object DOCX upload was accepted and stored.
- GREEN: the same targeted embedded-object test passed `1/1`.
- Upload security regression: approval supplement attachment security regression passed `15/15`.
- Rule regression: `RuleApplicationServiceTest` passed `103/103`.
- Contract guard: `ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure is a conservative OOXML entry-name guard, not a full document sandbox.
- OCR semantic extraction malware inspection, broader sandbox/deep parser analysis, and live ClamAV container smoke remain future production hardening.
