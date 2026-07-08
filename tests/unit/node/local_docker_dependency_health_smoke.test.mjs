import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildDockerInspectArgs,
  buildLocalDockerDependencyHealthPlan,
  parseDockerInspectState,
  summarizeLocalDockerDependencyHealth,
} from '../../../scripts/local-docker-dependency-health-smoke-lib.mjs';

test('buildLocalDockerDependencyHealthPlan covers required local delivery dependencies', () => {
  const plan = buildLocalDockerDependencyHealthPlan();

  assert.deepEqual(
    plan.map((dependency) => dependency.name),
    [
      'postgres',
      'redis',
      'minio',
      'rocketmq-namesrv',
      'rocketmq-broker',
      'opensearch',
      'milvus',
      'java-api',
      'higress',
      'etcd',
    ],
  );
  assert.equal(plan.find((dependency) => dependency.name === 'postgres').containerName, 'ir-postgres');
  assert.equal(plan.find((dependency) => dependency.name === 'higress').allowNoHealthcheck, true);
  assert.equal(plan.find((dependency) => dependency.name === 'etcd').allowNoHealthcheck, true);
});

test('buildDockerInspectArgs uses Docker State JSON format', () => {
  assert.deepEqual(buildDockerInspectArgs('ir-postgres'), [
    'inspect',
    '--format',
    '{{json .State}}',
    'ir-postgres',
  ]);
});

test('parseDockerInspectState extracts running and health status', () => {
  const state = parseDockerInspectState(JSON.stringify({
    Status: 'running',
    Running: true,
    ExitCode: 0,
    Error: '',
    Health: {
      Status: 'healthy',
      FailingStreak: 0,
    },
  }));

  assert.deepEqual(state, {
    status: 'running',
    running: true,
    exitCode: 0,
    error: '',
    healthStatus: 'healthy',
    healthFailingStreak: 0,
  });
});

test('summarizeLocalDockerDependencyHealth passes healthy containers and running allowlisted containers without healthchecks', () => {
  const plan = [
    { name: 'postgres', containerName: 'ir-postgres' },
    { name: 'higress', containerName: 'ir-higress', allowNoHealthcheck: true },
  ];
  const summary = summarizeLocalDockerDependencyHealth({
    plan,
    statesByContainer: new Map([
      ['ir-postgres', { running: true, status: 'running', healthStatus: 'healthy' }],
      ['ir-higress', { running: true, status: 'running' }],
    ]),
  });

  assert.equal(summary.passed, true);
  assert.equal(summary.classification, 'local-docker-dependencies-healthy');
  assert.deepEqual(summary.failedResults, []);
  assert.deepEqual(
    summary.results.map((result) => [result.name, result.passed, result.classification]),
    [
      ['postgres', true, 'container-healthy'],
      ['higress', true, 'container-running-no-healthcheck-allowed'],
    ],
  );
});

test('summarizeLocalDockerDependencyHealth fails closed for missing, exited, unhealthy, and unexpected no-healthcheck containers', () => {
  const plan = [
    { name: 'postgres', containerName: 'ir-postgres' },
    { name: 'minio', containerName: 'ir-minio' },
    { name: 'opensearch', containerName: 'ir-opensearch' },
    { name: 'java-api', containerName: 'ir-java-smoke' },
  ];
  const summary = summarizeLocalDockerDependencyHealth({
    plan,
    statesByContainer: new Map([
      ['ir-postgres', { running: false, status: 'exited', exitCode: 1 }],
      ['ir-minio', { running: true, status: 'running', healthStatus: 'unhealthy' }],
      ['ir-opensearch', { running: true, status: 'running' }],
    ]),
  });

  assert.equal(summary.passed, false);
  assert.equal(summary.classification, 'local-docker-dependencies-unhealthy');
  assert.deepEqual(
    summary.failedResults.map((result) => [result.name, result.classification]),
    [
      ['postgres', 'container-not-running'],
      ['minio', 'container-unhealthy'],
      ['opensearch', 'container-healthcheck-missing'],
      ['java-api', 'container-missing'],
    ],
  );
});
