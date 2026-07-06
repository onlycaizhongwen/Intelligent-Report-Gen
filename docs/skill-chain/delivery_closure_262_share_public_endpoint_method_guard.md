# Delivery Closure 262: Share Public Endpoint Method Guard

## Scope

- Requirement scope: `REQ-AUTH-001`, `REQ-REPORT-004`, public share-link controlled access and download flow.
- Production risk closed: share-link public paths were declared as `POST` controller endpoints, but the JWT bypass logic and Spring Security `permitAll` matching were path-centric. Unexpected HTTP methods on the same paths could bypass JWT before reaching business validation.

## Result

Public share-link ingress is now method-aware:

- Only `POST /api/v1/share-links/{shareToken}/access` can bypass JWT.
- Only `POST /api/v1/share-links/{shareToken}/report` can bypass JWT.
- Only `POST /api/v1/share-links/{shareToken}/exports/{exportFileId}/download-url` can bypass JWT.
- Unexpected methods on those paths now require a bearer token before downstream handling.
- Spring Security `permitAll` has the same `HttpMethod.POST` boundary as the JWT filter.

## Code Evidence

- `backend/java-report-core/src/main/java/com/company/report/shared/security/JwtAuthenticationFilter.java`
  - `isPublicPath(request.getMethod(), path)` now uses the request method directly.
  - The public share-link regexes only apply when the method is `POST`.
- `backend/java-report-core/src/main/java/com/company/report/shared/security/SecurityConfig.java`
  - Share-link `permitAll` matchers are restricted to `HttpMethod.POST`.
  - `/actuator/health` remains method-agnostic health access.
- `backend/java-report-core/src/test/java/com/company/report/shared/security/JwtAuthenticationFilterTest.java`
  - `publicShareEndpointsDoNotBypassJwtForUnexpectedHttpMethods` covers GET attempts on public share-link paths.

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtAuthenticationFilterTest#publicShareEndpointsDoNotBypassJwtForUnexpectedHttpMethods" test`
  - Failed because `GET /api/v1/share-links/share-token/access` bypassed JWT and reached the downstream filter.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtAuthenticationFilterTest" test`
  - `4/4` tests passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest,JwtTokenProviderTest,PermissionAspectTest,JwtAuthenticationFilterTest" test`
  - `29/29` tests passed.

## Residual Risk

This closure proves method-aware behavior at the filter and source contract level. It does not replace live gateway route checks, full MVC 401/403/200 coverage, or external share-link abuse testing through Higress.
