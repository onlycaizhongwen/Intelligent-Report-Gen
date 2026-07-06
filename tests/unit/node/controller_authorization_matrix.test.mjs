import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

import {
  buildJavaControllerAuthorizationMatrix,
  findControllerAuthorizationGaps,
  findControllerPermissionCatalogGaps,
  renderControllerAuthorizationMatrixMarkdown,
} from '../../../scripts/controller-authorization-matrix-lib.mjs';
import {
  buildHigressPermissionCatalogAuthorizationChecks,
} from '../../../scripts/higress-gateway-smoke-lib.mjs';

const controllersRoot = 'backend/java-report-core/src/main/java/com/company/report';
const matrixDocPath = 'docs/skill-chain/java_controller_authorization_matrix.md';

test('buildJavaControllerAuthorizationMatrix extracts endpoint-level security boundaries', () => {
  const matrix = buildJavaControllerAuthorizationMatrix({ controllersRoot });

  assert.ok(matrix.length > 70, 'expected endpoint-by-endpoint controller matrix');

  assert.deepEqual(
    pick(matrix, 'GET', '/api/v1/history'),
    {
      method: 'GET',
      path: '/api/v1/history',
      boundary: 'authenticated',
      permission: null,
      controller: 'AuditController',
      handler: 'history',
    },
  );
  assert.deepEqual(
    pick(matrix, 'POST', '/api/v1/share-links/{shareToken}/access'),
    {
      method: 'POST',
      path: '/api/v1/share-links/{shareToken}/access',
      boundary: 'public',
      permission: null,
      controller: 'PermissionController',
      handler: 'accessShare',
    },
  );
  assert.deepEqual(
    pick(matrix, 'POST', '/api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments'),
    {
      method: 'POST',
      path: '/api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments',
      boundary: 'permission',
      permission: 'rule:debug',
      controller: 'RuleController',
      handler: 'uploadApprovalSupplementAttachment',
    },
  );
});

test('findControllerAuthorizationGaps fails if any controller mapping lacks an explicit boundary', () => {
  const matrix = buildJavaControllerAuthorizationMatrix({ controllersRoot });
  const gaps = findControllerAuthorizationGaps(matrix);

  assert.deepEqual(gaps, []);

  const keys = matrix.map((entry) => `${entry.method} ${entry.path}`);
  assert.equal(new Set(keys).size, keys.length, 'controller authorization matrix should not contain duplicate method/path entries');
});

test('java controller authorization matrix document stays synchronized with source', () => {
  const matrix = buildJavaControllerAuthorizationMatrix({ controllersRoot });
  const expected = renderControllerAuthorizationMatrixMarkdown(matrix);
  const actual = fs.readFileSync(matrixDocPath, 'utf8');

  assert.equal(actual, expected);
  assert.match(actual, /\| POST \| `\/api\/v1\/share-links\/\{shareToken\}\/access` \| public \|/);
  assert.doesNotMatch(actual, /unclassified/);
});

test('controller permissions stay covered by the Higress permission catalog probes', () => {
  const matrix = buildJavaControllerAuthorizationMatrix({ controllersRoot });
  const catalogPermissions = new Set(
    buildHigressPermissionCatalogAuthorizationChecks()
      .map((check) => check.permission)
      .filter(Boolean),
  );

  assert.equal(catalogPermissions.size, 16, 'expected one Higress catalog probe family per RBAC permission');
  assert.deepEqual(findControllerPermissionCatalogGaps(matrix, catalogPermissions), []);
});

function pick(matrix, method, path) {
  const found = matrix.find((entry) => entry.method === method && entry.path === path);
  assert.ok(found, `${method} ${path} should be present`);
  return {
    method: found.method,
    path: found.path,
    boundary: found.boundary,
    permission: found.permission,
    controller: found.controller,
    handler: found.handler,
  };
}
