# Delivery Closure 330: UC-08 Approval Supplement Image Active Content

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement image inspection now rejects obvious active-content payloads before object storage:

- The inspector still verifies declared PNG/JPEG magic signatures first.
- PNG/JPEG uploads are then scanned for active content markers such as `<script`, `<svg`, `<html`, `javascript:`, `onload=`, and `<?php`.
- Rejections use `rejectionReason=image_active_content_detected`.
- Rejected uploads preserve `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine=basic_attachment_content_inspector`.
- Rejected images stop before `DocumentStorage.store(...)`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenImageContainsActiveContentBeforeStorageWrite" test` first failed because the PNG upload was accepted and stored.
- GREEN: the same targeted image active-content test passed `1/1`.
- Upload security regression: approval supplement attachment security regression passed `14/14`.
- Rule regression: `RuleApplicationServiceTest` passed `102/102`.
- Contract guard: `ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure is a conservative image payload guard, not full OCR semantic malware detection.
- Broader sandbox/deep parser analysis and live ClamAV container smoke remain future production hardening.
