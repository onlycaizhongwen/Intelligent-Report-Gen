import test from 'node:test';
import assert from 'node:assert/strict';

import {
  resolveSmokeCommand,
} from '../../../scripts/local-smoke-runner.mjs';

test('resolveSmokeCommand wraps npm and cmd scripts through cmd.exe on Windows', () => {
  assert.deepEqual(
    resolveSmokeCommand(
      { command: 'npm', args: ['run', 'e2e:real-backend', '--', 'tests/e2e/share-real-backend.spec.ts'] },
      'win32',
    ),
    {
      command: 'cmd.exe',
      args: ['/d', '/s', '/c', 'npm run e2e:real-backend -- tests/e2e/share-real-backend.spec.ts'],
    },
  );

  assert.deepEqual(
    resolveSmokeCommand(
      { command: '.\\mvnw.cmd', args: ['-pl', 'backend/java-report-core', '-Dtest=ExampleTest', 'test'] },
      'win32',
    ),
    {
      command: 'cmd.exe',
      args: ['/d', '/s', '/c', '.\\mvnw.cmd -pl backend/java-report-core -Dtest=ExampleTest test'],
    },
  );
});

test('resolveSmokeCommand keeps native commands outside Windows', () => {
  assert.deepEqual(
    resolveSmokeCommand({ command: './mvnw', args: ['-pl', 'backend/java-report-core', 'test'] }, 'linux'),
    {
      command: './mvnw',
      args: ['-pl', 'backend/java-report-core', 'test'],
    },
  );
});
