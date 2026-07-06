# Delivery Closure 325: UC-08 Approval Supplement Office Archive Inspection

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement attachment inspection now opens allowed OOXML Office archives before object storage writes:

- `.docx` and `.xlsx` uploads are still checked for the expected ZIP file signature.
- Office archive entries are inspected for the EICAR antivirus test signature.
- Office archive macro payloads such as `vbaProject.bin` are rejected with `rejectionReason=macro_payload_detected`.
- Archive malware signatures are rejected with `rejectionReason=malware_signature_detected`.
- Rejected uploads continue to write `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine`, `inspectionMessage`, `fileName`, `contentType`, and policy fields.
- Rejected archive uploads stop before `DocumentStorage.store(...)`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsMalwareSignatureBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsMacroPayloadBeforeStorageWrite" test` first failed `2/2` because both Office archives were accepted.
- GREEN: the same targeted archive-inspection command passed `2/2`.
- Upload security regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#uploadsApprovalSupplementAttachmentToRuleScopedStorageAndWritesAudit+rejectsApprovalSupplementAttachmentWhenContentTypeIsNotAllowedAndWritesAudit+rejectsApprovalSupplementAttachmentWhenFileIsTooLargeAndWritesAudit+usesConfiguredApprovalSupplementAttachmentMaxSizeBeforeStorageWrite+usesConfiguredApprovalSupplementAttachmentAllowedTypesBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenMalwareSignatureIsDetectedBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenDeclaredPdfDoesNotMatchFileSignatureBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsMalwareSignatureBeforeStorageWrite+rejectsApprovalSupplementAttachmentWhenOfficeArchiveContainsMacroPayloadBeforeStorageWrite" test` passed `9/9`.
- Rule regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest" test` passed `97/97`.
- Contract: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract" test` passed `1/1`.
- Prod profile context: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportCoreProdProfileContextTest" test` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure is not a full external antivirus deployment.
- Encrypted archive handling, ZIP-bomb throttling, OCR/image malware inspection, and sandbox/deep parser analysis remain future production hardening.
