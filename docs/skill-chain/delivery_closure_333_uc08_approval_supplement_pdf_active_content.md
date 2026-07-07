# Delivery Closure 333: UC-08 Approval Supplement PDF Active Content

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement PDF inspection now rejects obvious active-content payloads before object storage:

- The inspector still verifies declared PDF magic signatures first.
- PDF bytes are then scanned for conservative active-content markers such as `/JavaScript`, `/JS`, `/OpenAction`, `/Launch`, `/EmbeddedFile`, and `/RichMedia`.
- Rejections use `rejectionReason=pdf_active_content_detected`.
- Rejected uploads preserve `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine=basic_attachment_content_inspector`.
- Rejected PDF files stop before `DocumentStorage.store(...)`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenPdfContainsActiveContentBeforeStorageWrite" test` first failed because the scripted PDF upload was accepted and stored.
- GREEN: the same targeted PDF active-content test passed `1/1`.
- Upload security regression: approval supplement attachment security regression passed `17/17`.
- Rule regression: `RuleApplicationServiceTest` passed `105/105`.
- Contract guard: `ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure is a conservative marker guard, not a full PDF parser or sandbox.
- OCR semantic extraction malware inspection, broader sandbox/deep parser analysis, and live ClamAV container smoke remain future production hardening.
