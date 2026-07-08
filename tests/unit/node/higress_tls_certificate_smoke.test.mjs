import test from 'node:test';
import assert from 'node:assert/strict';

import {
  evaluateTlsCertificate,
  readTlsCertificateAuthority,
  runHigressTlsCertificateSmoke,
} from '../../../scripts/higress-tls-certificate-smoke-lib.mjs';

const NOW = new Date('2026-07-07T00:00:00Z');

test('evaluateTlsCertificate accepts trusted certificates with enough validity window', () => {
  const result = evaluateTlsCertificate({
    authorized: true,
    authorizationError: null,
    certificate: {
      subject: { CN: 'reports.example.com' },
      issuer: { CN: 'Example Managed CA' },
      valid_to: 'Aug 10 00:00:00 2026 GMT',
      fingerprint256: 'AA:BB',
    },
    now: NOW,
    minValidDays: 14,
  });

  assert.deepEqual(result, {
    passed: true,
    classification: 'tls-trusted',
    daysRemaining: 34,
    authorizationError: null,
    subject: { CN: 'reports.example.com' },
    issuer: { CN: 'Example Managed CA' },
    validTo: 'Aug 10 00:00:00 2026 GMT',
    fingerprint256: 'AA:BB',
  });
});

test('evaluateTlsCertificate rejects untrusted certificate chains', () => {
  const result = evaluateTlsCertificate({
    authorized: false,
    authorizationError: 'DEPTH_ZERO_SELF_SIGNED_CERT',
    certificate: {
      subject: { CN: 'localhost' },
      issuer: { CN: 'localhost' },
      valid_to: 'Aug 10 00:00:00 2026 GMT',
    },
    now: NOW,
    minValidDays: 14,
  });

  assert.equal(result.passed, false);
  assert.equal(result.classification, 'tls-untrusted');
  assert.equal(result.authorizationError, 'DEPTH_ZERO_SELF_SIGNED_CERT');
});

test('evaluateTlsCertificate rejects certificates that expire inside the required window', () => {
  const result = evaluateTlsCertificate({
    authorized: true,
    authorizationError: null,
    certificate: {
      subject: { CN: 'reports.example.com' },
      issuer: { CN: 'Example Managed CA' },
      valid_to: 'Jul 15 00:00:00 2026 GMT',
    },
    now: NOW,
    minValidDays: 14,
  });

  assert.equal(result.passed, false);
  assert.equal(result.classification, 'tls-expiring');
  assert.equal(result.daysRemaining, 8);
});

test('runHigressTlsCertificateSmoke connects with TLS verification enabled', async () => {
  const calls = [];
  const result = await runHigressTlsCertificateSmoke({
    host: 'gateway.example.com',
    port: 443,
    servername: 'reports.example.com',
    now: NOW,
    minValidDays: 14,
    connectTls: async (options) => {
      calls.push(options);
      return {
        authorized: true,
        authorizationError: null,
        getPeerCertificate: () => ({
          subject: { CN: 'reports.example.com' },
          issuer: { CN: 'Example Managed CA' },
          valid_to: 'Aug 10 00:00:00 2026 GMT',
          fingerprint256: 'AA:BB',
        }),
        end: () => {},
      };
    },
  });

  assert.equal(result.passed, true);
  assert.equal(result.classification, 'tls-trusted');
  assert.deepEqual(calls, [{
    host: 'gateway.example.com',
    port: 443,
    servername: 'reports.example.com',
    rejectUnauthorized: true,
  }]);
});

test('runHigressTlsCertificateSmoke accepts a custom CA bundle for private PKI', async () => {
  const calls = [];
  const result = await runHigressTlsCertificateSmoke({
    host: 'gateway.internal.example',
    port: 443,
    servername: 'reports.internal.example',
    ca: '-----BEGIN CERTIFICATE-----\ncustomer-ca\n-----END CERTIFICATE-----\n',
    now: NOW,
    minValidDays: 14,
    connectTls: async (options) => {
      calls.push(options);
      return {
        authorized: true,
        authorizationError: null,
        getPeerCertificate: () => ({
          subject: { CN: 'reports.internal.example' },
          issuer: { CN: 'Customer Private CA' },
          valid_to: 'Aug 10 00:00:00 2026 GMT',
          fingerprint256: 'AA:CC',
        }),
        end: () => {},
      };
    },
  });

  assert.equal(result.passed, true);
  assert.equal(result.caConfigured, true);
  assert.deepEqual(calls, [{
    host: 'gateway.internal.example',
    port: 443,
    servername: 'reports.internal.example',
    rejectUnauthorized: true,
    ca: '-----BEGIN CERTIFICATE-----\ncustomer-ca\n-----END CERTIFICATE-----\n',
  }]);
});

test('readTlsCertificateAuthority reads optional CA files', () => {
  const ca = readTlsCertificateAuthority({
    caFile: 'config/certs/customer-ca.pem',
    readFile: (path, encoding) => {
      assert.equal(path, 'config/certs/customer-ca.pem');
      assert.equal(encoding, 'utf8');
      return '-----BEGIN CERTIFICATE-----\ncustomer-ca\n-----END CERTIFICATE-----\n';
    },
  });

  assert.equal(ca, '-----BEGIN CERTIFICATE-----\ncustomer-ca\n-----END CERTIFICATE-----\n');
  assert.equal(readTlsCertificateAuthority({ caFile: '' }), undefined);
});

test('runHigressTlsCertificateSmoke returns structured failure when TLS verification rejects', async () => {
  const result = await runHigressTlsCertificateSmoke({
    host: '127.0.0.1',
    port: 18443,
    now: NOW,
    connectTls: async () => {
      throw Object.assign(new Error('self-signed certificate'), {
        code: 'DEPTH_ZERO_SELF_SIGNED_CERT',
      });
    },
  });

  assert.equal(result.passed, false);
  assert.equal(result.classification, 'tls-untrusted');
  assert.equal(result.authorizationError, 'DEPTH_ZERO_SELF_SIGNED_CERT');
  assert.equal(result.subject.CN, undefined);
});

test('runHigressTlsCertificateSmoke omits SNI when host is an IP and no server name is provided', async () => {
  const calls = [];
  await runHigressTlsCertificateSmoke({
    host: '127.0.0.1',
    port: 18443,
    now: NOW,
    connectTls: async (options) => {
      calls.push(options);
      return {
        authorized: true,
        authorizationError: null,
        getPeerCertificate: () => ({
          subject: { CN: 'localhost' },
          issuer: { CN: 'Local Dev CA' },
          valid_to: 'Aug 10 00:00:00 2026 GMT',
        }),
        end: () => {},
      };
    },
  });

  assert.deepEqual(calls, [{
    host: '127.0.0.1',
    port: 18443,
    rejectUnauthorized: true,
  }]);
});
