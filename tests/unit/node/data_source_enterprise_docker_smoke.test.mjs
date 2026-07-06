import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildEnterpriseDataSourceDefinitions,
  buildEnterpriseDataSourceSmokePlan,
} from '../../../scripts/data-source-enterprise-docker-smoke-lib.mjs';

test('buildEnterpriseDataSourceSmokePlan covers ERP, OA and finance source routes', () => {
  const plan = buildEnterpriseDataSourceSmokePlan({
    apiBaseUrl: 'http://127.0.0.1:28082/api/v1',
    fixtureOrigin: 'http://host.docker.internal:29090',
    mysqlJdbcHost: 'host.docker.internal',
    mysqlPort: 13306,
  });

  assert.equal(plan.apiBaseUrl, 'http://127.0.0.1:28082/api/v1');
  assert.deepEqual(plan.requiredContainers, ['ir-java-smoke', 'ir-postgres']);
  assert.deepEqual(plan.optionalContainers, ['intelligent-report-system-mysql-1']);
  assert.deepEqual(plan.sources.map((source) => source.key), ['erp-mysql', 'oa-api', 'finance-api']);
  assert.deepEqual(plan.sources.map((source) => source.expectedProcessedRows), [2, 2, 2]);
});

test('buildEnterpriseDataSourceDefinitions uses real JDBC and HTTP connector settings', () => {
  const definitions = buildEnterpriseDataSourceDefinitions({
    fixtureOrigin: 'http://host.docker.internal:29090',
    mysqlJdbcHost: 'host.docker.internal',
    mysqlPort: 13306,
  });

  const erp = definitions.find((source) => source.key === 'erp-mysql');
  const oa = definitions.find((source) => source.key === 'oa-api');
  const finance = definitions.find((source) => source.key === 'finance-api');

  assert.equal(erp.dataSource.sourceType, 'mysql');
  assert.match(erp.dataSource.endpoint, /^jdbc:mysql:\/\/host\.docker\.internal:13306\/erp_source/);
  assert.equal(erp.dataSource.username, 'erp');
  assert.equal(erp.dataSource.cursorColumn, 'id');
  assert.match(erp.dataSource.syncQuery, /smoke_erp_orders/);

  assert.equal(oa.dataSource.sourceType, 'api');
  assert.equal(oa.dataSource.endpoint, 'http://host.docker.internal:29090/oa/documents');
  assert.equal(oa.dataSource.fieldMapping.authType, 'bearer');
  assert.equal(oa.dataSource.fieldMapping.rowsPath, 'data.documents');

  assert.equal(finance.dataSource.sourceType, 'api');
  assert.equal(finance.dataSource.endpoint, 'http://host.docker.internal:29090/finance/vouchers');
  assert.equal(finance.dataSource.fieldMapping.method, 'POST');
  assert.equal(finance.dataSource.fieldMapping.authType, 'api_key');
  assert.equal(finance.dataSource.fieldMapping.headers['X-Tenant'], 'finance');
});
