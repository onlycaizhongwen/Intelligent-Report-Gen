# Delivery Closure 332: UC-08 Approval Supplement Legacy Office Macros

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement legacy Office inspection now rejects obvious macro-bearing CFB files before object storage:

- The inspector still verifies `application/msword` and `application/vnd.ms-excel` CFB magic signatures first.
- Legacy Office bytes are then scanned for conservative VBA/macro markers such as `VBA_PROJECT`, `VBA/`, `VBA`, and `macros`.
- Rejections use `rejectionReason=legacy_office_macro_detected`.
- Rejected uploads preserve `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine=basic_attachment_content_inspector`.
- Rejected legacy Office files stop before `DocumentStorage.store(...)`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenLegacyOfficeContainsMacroMarkerBeforeStorageWrite" test` first failed because the legacy `.doc` upload was accepted and stored.
- GREEN: the same targeted legacy Office macro-marker test passed `1/1`.
- Upload security regression: approval supplement attachment security regression passed `16/16`.
- Rule regression: `RuleApplicationServiceTest` passed `104/104`.
- Contract guard: `ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure is a conservative CFB marker guard, not a full legacy Office parser or sandbox.
- OCR semantic extraction malware inspection, broader sandbox/deep parser analysis, and live ClamAV container smoke remain future production hardening.
