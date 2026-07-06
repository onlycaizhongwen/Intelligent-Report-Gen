# UC-08 Approval Supplement Attachment Upload Closure

Date: 2026-07-06

## Scope

- Requirement: `REQ-RULE-001`
- User journey: `UC-08` rule approval rejection -> supplement -> resubmission.
- Prototype surface: `/rules/approvals`, rejected approval card.

## Result

Rejected approval supplement now supports a real attachment upload before resubmission.

- Backend exposes `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments`.
- Endpoint is protected by `rule:debug` and consumes `multipart/form-data`.
- Service validates the approval record belongs to the rule and is in `rejected` status.
- File storage uses the existing `DocumentStorage` boundary, so local/production MinIO wiring is reused instead of adding a separate storage path.
- The uploaded object is stored under `approval-supplements/rule-{ruleId}/approval-{approvalRecordId}/{uuid}/{fileName}`.
- Response returns `fileName`, `bucket`, `objectKey`, `sizeBytes`, `contentType`, and `evidenceUrl`.
- Frontend `ApprovalInbox` lets users choose `Supplement attachment`; upload result fills the existing supplement `evidenceUrl`, then `Submit supplement` keeps using the established resubmission API.
- Audit writes `rule_approval_supplement_attachment_uploaded` with approval record, run/node, object metadata, and evidence URL.

## Evidence

- RED: `npm run test -- src/api/apiContracts.test.ts` failed with `ruleApi.uploadApprovalSupplementAttachment is not a function`.
- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard,RuleApplicationServiceTest#uploadsApprovalSupplementAttachmentToRuleScopedStorageAndWritesAudit" test` failed because the multipart endpoint, service constructor, and upload method were missing.
- GREEN: `npm run test -- src/api/apiContracts.test.ts` passed `30/30`.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleApprovalActionsAllowAssigneeDebugPermissionWithServiceGuard,RuleApplicationServiceTest#uploadsApprovalSupplementAttachmentToRuleScopedStorageAndWritesAudit" test` passed `2/2`.
- GREEN: `npm run typecheck` passed.
- GREEN: `npm run e2e -- approval-inbox.spec.ts -g "submits supplement"` passed `1/1` in Chromium and verified multipart upload plus supplement payload evidence URL.

## Remaining Gaps

- Real MinIO container smoke for this exact endpoint still needs to be added to the broader local P3 bundle.
- File size/type policy, malware scanning, and enterprise retention policy are not yet enforced on approval supplement attachments.
- Higress-routed browser acceptance for this exact upload path is still open.
