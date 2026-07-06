# Delivery Closure 267: Share Link MVC Runtime Boundary

Date: 2026-07-02

## Scope

This closure continues the production-readiness loop for external report sharing, mapped to `REQ-AUTH-001` and the prototype requirement that external users can access shared report links under controlled security boundaries.

## Result

- Added MockMvc runtime coverage for the real Spring Security filter chain around share-link routes.
- Verified unauthenticated `POST /api/v1/share-links/{shareToken}/access` reaches the business layer as a public share-token challenge endpoint.
- Verified unauthenticated `GET /api/v1/share-links/{shareToken}/access` does not bypass bearer authentication.
- Verified authenticated share-management creation `POST /api/v1/reports/{reportId}/share-links` still requires bearer authentication.

The test uses a mocked `PermissionApplicationService` only to prove whether the request crossed the security boundary into the controller/business layer. It does not replace service-level share token persistence and audit tests.

## Mutation Evidence

The guard was mutation-checked by temporarily narrowing the Spring Security public matcher for:

```text
/api/v1/share-links/*/access
```

Expected failure was observed:

- Test: `ShareLinkSecurityRuntimeContractTest#publicShareAccessPostReachesBusinessLayerWithoutBearerToken`
- Failure reason: public share `POST /access` returned `403` instead of reaching the controller with `200`

The temporary mutation was then restored.

## Verification

Target GREEN:

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ShareLinkSecurityRuntimeContractTest" test
```

Result:

- Tests run: 3
- Failures: 0
- Errors: 0
- Skipped: 0

Regression:

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest,ContractSurfaceTest,JwtTokenProviderTest,PermissionAspectTest,JwtAuthenticationFilterTest,SecurityRuntimeContractTest,ShareLinkSecurityRuntimeContractTest" test
```

Result:

- Tests run: 65
- Failures: 0
- Errors: 0
- Skipped: 0

## Residual Risk

This closure proves Java MVC/Spring Security boundary behavior locally. It does not prove live Higress route policy, OIDC integration, browser share-link UX, or real external network abuse resistance.
