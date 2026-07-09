import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

test('prototype acceptance matrix is readable Simplified Chinese and keeps closure columns', () => {
  const content = readFileSync('docs/skill-chain/prototype_requirement_acceptance_matrix.md', 'utf8');

  assert.match(content, /^# 原型需求交付验收矩阵/m);
  assert.match(content, /页面/);
  assert.match(content, /需求/);
  assert.match(content, /验收标准/);
  assert.match(content, /验证证据/);
  assert.doesNotMatch(content, /鍘|闇|骞|鈥|涓|妯|璧|鎶/);
});
