# Delivery Closure 329: UC-08 Approval Supplement Office External Relationships

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement Office archive inspection now includes a minimal deep-parser guard for OOXML relationship files:

- The inspector reads `.rels` entries while streaming the Office archive.
- Relationship files declaring `TargetMode=External` are rejected.
- Relationship files containing HTTP or HTTPS `Target` values are rejected.
- Rejections use `rejectionReason=external_relationship_detected`.
- Rejected uploads preserve `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine=basic_attachment_content_inspector`.
- Rejected archives stop before `DocumentStorage.store(...)`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsExternalRelationshipBeforeStorageWrite" test` first failed because the malicious relationship archive was accepted and stored.
- GREEN: the same targeted external relationship test passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure is a focused deep-parser guard, not a full document sandbox.
- OCR/image malware inspection, broader sandbox/deep parser analysis, and live ClamAV container smoke remain future production hardening.
