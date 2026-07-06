# Delivery Closure 324: UC-08 Approval Supplement Attachment Content Inspection

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement attachment uploads now run a backend content inspection step before object storage writes:

- `RuleApprovalSupplementAttachmentInspector` defines the inspection port.
- `BasicRuleApprovalSupplementAttachmentInspector` is the default Spring bean and constructor fallback.
- EICAR antivirus test signatures are rejected with `rejectionReason=malware_signature_detected`.
- Declared binary content types are checked against file signatures for PDF, JPEG, PNG, OLE Office, and OOXML Office files.
- Signature mismatches are rejected with `rejectionReason=content_signature_mismatch`.
- Rejected uploads write `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine`, `inspectionMessage`, `contentType`, `sizeBytes`, and the policy fields from Closure 322.
- Valid evidence uploads still proceed to `DocumentStorage.store(...)`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenMalwareSignatureIsDetectedBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenDeclaredPdfDoesNotMatchFileSignatureBeforeStorageWrite" test` first failed because both uploads were accepted.
- GREEN: the same targeted content-inspection command passed `2/2`.
- Upload policy regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#uploadsApprovalSupplementAttachmentToRuleScopedStorageAndWritesAudit+rejectsApprovalSupplementAttachmentWhenContentTypeIsNotAllowedAndWritesAudit+rejectsApprovalSupplementAttachmentWhenFileIsTooLargeAndWritesAudit+usesConfiguredApprovalSupplementAttachmentMaxSizeBeforeStorageWrite+usesConfiguredApprovalSupplementAttachmentAllowedTypesBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenMalwareSignatureIsDetectedBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenDeclaredPdfDoesNotMatchFileSignatureBeforeStorageWrite" test` passed `7/7`.
- Rule regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest" test` passed `95/95`.
- Contract: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract" test` passed `1/1`.
- Prod profile context: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportCoreProdProfileContextTest" test` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApplicationService.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure is not a full external antivirus deployment.
- Office archive malware/macro inspection is added in Closure 325; external AV engine integration, encrypted archive handling, ZIP-bomb throttling, OCR/image malware inspection, and sandbox/deep parser analysis remain future production hardening.
