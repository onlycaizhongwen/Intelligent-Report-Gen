import tls from 'node:tls';
import net from 'node:net';
import fs from 'node:fs';

function connectTlsSocket(options) {
  return new Promise((resolve, reject) => {
    const socket = tls.connect(options, () => resolve(socket));
    socket.once('error', reject);
  });
}

function wholeDaysUntil(validTo, now) {
  const validToTime = Date.parse(validTo);
  if (Number.isNaN(validToTime)) {
    return null;
  }
  return Math.floor((validToTime - now.getTime()) / 86_400_000);
}

export function readTlsCertificateAuthority({ caFile, readFile = fs.readFileSync } = {}) {
  if (typeof caFile !== 'string' || caFile.trim().length === 0) {
    return undefined;
  }
  return readFile(caFile, 'utf8');
}

export function evaluateTlsCertificate({
  authorized,
  authorizationError,
  certificate,
  now = new Date(),
  minValidDays = 14,
} = {}) {
  const daysRemaining = wholeDaysUntil(certificate?.valid_to, now);
  const base = {
    passed: false,
    classification: 'tls-untrusted',
    daysRemaining,
    authorizationError: authorizationError ?? null,
    subject: certificate?.subject ?? {},
    issuer: certificate?.issuer ?? {},
    validTo: certificate?.valid_to ?? null,
    fingerprint256: certificate?.fingerprint256 ?? null,
  };

  if (!authorized) {
    return base;
  }
  if (daysRemaining == null || daysRemaining < minValidDays) {
    return {
      ...base,
      classification: 'tls-expiring',
    };
  }
  return {
    ...base,
    passed: true,
    classification: 'tls-trusted',
  };
}

export async function runHigressTlsCertificateSmoke({
  host = '127.0.0.1',
  port = 18443,
  servername,
  ca,
  minValidDays = 14,
  now = new Date(),
  connectTls = connectTlsSocket,
} = {}) {
  let socket;
  const effectiveServername = servername ?? (net.isIP(host) ? undefined : host);
  const caConfigured = typeof ca === 'string' && ca.trim().length > 0;
  const connectionOptions = {
    host,
    port,
    rejectUnauthorized: true,
    ...(caConfigured ? { ca } : {}),
    ...(effectiveServername ? { servername: effectiveServername } : {}),
  };
  try {
    socket = await connectTls(connectionOptions);
    const result = evaluateTlsCertificate({
      authorized: socket.authorized,
      authorizationError: socket.authorizationError,
      certificate: socket.getPeerCertificate(true),
      now,
      minValidDays,
    });
    return {
      host,
      port,
      servername: effectiveServername ?? null,
      minValidDays,
      caConfigured,
      ...result,
    };
  } catch (error) {
    return {
      host,
      port,
      servername: effectiveServername ?? null,
      minValidDays,
      caConfigured,
      ...evaluateTlsCertificate({
        authorized: false,
        authorizationError: error?.code ?? error?.message ?? 'TLS_CONNECTION_FAILED',
        certificate: null,
        now,
        minValidDays,
      }),
    };
  } finally {
    socket?.end?.();
  }
}
