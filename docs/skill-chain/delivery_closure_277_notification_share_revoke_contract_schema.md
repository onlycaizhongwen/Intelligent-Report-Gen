# Closure 277: Notification and Share Revoke Contract Schema

## Scope

- Requirement chain: `REQ-AUTH-001`, `REQ-COLLAB-001`, `audit-history-dashboard`
- Phase artifact: `S4 API Contract`
- Target endpoints:
  - `GET /api/v1/system-alerts`
  - `POST /api/v1/share-links/{shareToken}/revoke`

## Result

System alert listing and internal share revocation APIs no longer expose only generic `business response data` in the backend supplement. The API contract now declares backend-real field-level response schemas for notification readiness and share security governance.

- System alert page fields: `items`, `page`, `pageSize`, `total`
- System alert item fields: `alertId`, `recipientUserId`, `type`, `severity`, `status`, `resourceType`, `resourceId`, `payload`, `createdAt`
- Share revoke fields: `shareLinkId`, `reportId`, `createdBy`, `shareToken`, `shareUrl`, `status`, `expiresAt`, `passwordRequired`, `allowDownload`, `allowedDownloadFormats`, `maxAccessCount`, `allowedVisitors`, `allowedVisitorDomains`, `singleUse`

## Evidence

- Contract guard: `ContractSurfaceTest#notificationAndShareRevokeEndpointsDeclareFieldLevelResponseContracts`
- API contract: `docs/skill-chain/api_contract.md` section `13.2`
- Backend field source: `SystemAlertApplicationService#toResponse` and `PermissionApplicationService#shareLinkResponse`

## TDD Evidence

RED:

```text
ContractSurfaceTest.notificationAndShareRevokeEndpointsDeclareFieldLevelResponseContracts
GET /api/v1/system-alerts responseBody.data.properties
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#notificationAndShareRevokeEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Remaining Gaps

- This closure did not rerun live Higress route smoke or browser notification/share-management E2E.
- Report templates, report version diff, and rule-engine endpoints still have generic schemas and should be deepened in subsequent closures.
