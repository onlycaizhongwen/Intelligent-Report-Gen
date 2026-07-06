# Delivery Closure 328: UC-08 Approval Supplement Encrypted Archive Handling

## Scope

- Requirement: `REQ-RULE-001`
- Journey: `UC-08` rule orchestration and approval supplement resubmission
- Endpoint: `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`

## Result

Approval supplement Office archive inspection now rejects encrypted OOXML archives before object storage writes:

- The inspector scans ZIP local file headers and central directory headers before decompression.
- If an entry declares the ZIP encryption flag, inspection rejects the upload with `rejectionReason=encrypted_archive_unsupported`.
- Rejected uploads preserve `rule_approval_supplement_attachment_rejected` audit evidence with `inspectionEngine=basic_attachment_content_inspector`.
- Rejected encrypted archives stop before `DocumentStorage.store(...)`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#rejectsApprovalSupplementAttachmentWhenOfficeArchiveIsEncryptedBeforeStorageWrite" test` first failed because the upload was rejected only with a generic content-inspection failure instead of structured rejection evidence.
- GREEN: the same targeted encrypted archive test passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/rule/application/BasicRuleApprovalSupplementAttachmentInspector.java`
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- OCR/image malware inspection and sandbox/deep parser analysis remain future production hardening.
- Live ClamAV container smoke remains pending because it requires a local infrastructure dependency.
