// node --test scripts/beta — no network, no Android SDK: in-memory storage + canned aapt2/apksigner output.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  parseBadging, parseSigners, publish, selectPrunable, sha256, validateApk, versionedPathname,
} from './publish-beta.mjs';

const CERT = 'ab'.repeat(32);
const signers = { digests: [CERT], dn: 'CN=TANY Staging, O=TANY' };
const info = (versionCode, packageName = 'ma.tany.client.staging') => ({ packageName, versionCode, versionName: '0.1.0-staging' });

function memoryStorage({ failPut } = {}) {
  const blobs = new Map();
  let etag = 0;
  const url = (p) => `https://store.public.blob.vercel-storage.com/${p}`;
  return {
    blobs,
    async head(p) { const b = blobs.get(p); return b ? { url: url(p), etag: b.etag } : null; },
    async put(p, body, opts) {
      if (failPut?.(p)) throw new Error(`upload failed: ${p}`);
      const existing = blobs.get(p);
      if (existing && opts.allowOverwrite === false) throw new Error('already exists');
      if (opts.ifMatch && existing?.etag !== opts.ifMatch) throw new Error('precondition failed');
      blobs.set(p, { body: Buffer.from(body), etag: `e${++etag}` });
      return { url: url(p), pathname: p };
    },
    async list(prefix) { return [...blobs.keys()].filter((p) => p.startsWith(prefix)).map((p) => ({ pathname: p, url: url(p) })); },
    async del(urls) { for (const u of urls) blobs.delete(u.replace(url(''), '')); },
    async fetch(u) {
      const b = blobs.get(u.split('?')[0].replace(url(''), ''));
      return b
        ? { ok: true, status: 200, json: async () => JSON.parse(b.body), arrayBuffer: async () => b.body }
        : { ok: false, status: 404 };
    },
  };
}
const quiet = () => {};
const run = (storage, versionCode, extra = {}) => publish({
  appKey: 'tany', apk: Buffer.from(`apk-${versionCode}`), info: info(versionCode), signers, expectedCert: CERT,
  commit: '7d2e3ec9f0', storage, log: quiet, now: new Date('2026-10-07T12:32:00Z'), ...extra,
});

test('parses aapt2 badging and apksigner output', () => {
  assert.deepEqual(
    parseBadging("package: name='ma.tany.client.staging' versionCode='34' versionName='0.1.0-staging' platformBuildVersionName='16'\nsdkVersion:'26'"),
    { packageName: 'ma.tany.client.staging', versionCode: 34, versionName: '0.1.0-staging' },
  );
  const s = parseSigners(`Signer #1 certificate DN: CN=TANY Staging\nSigner #1 certificate SHA-256 digest: ${CERT.toUpperCase()}\n`);
  assert.deepEqual(s, { digests: [CERT], dn: 'CN=TANY Staging' });
});

test('refuses PROD packages, debug keys and an unexpected certificate', () => {
  assert.match(validateApk('tany', info(5, 'ma.tany.client'), signers, CERT).join(), /only STAGING/);
  assert.match(validateApk('collect', info(5), signers, CERT).join(), /ma\.tany\.collect\.staging/);
  assert.match(validateApk('tany', info(5), { digests: [CERT], dn: 'CN=Android Debug,O=Android' }, CERT).join(), /debug key/);
  assert.match(validateApk('tany', info(5), signers, 'cd'.repeat(32)).join(), /expected stable STAGING key/);
  assert.match(validateApk('tany', info(5), signers, '').join(), /BETA_EXPECTED_CERT_SHA256/);
  assert.deepEqual(validateApk('tany', info(5), signers, CERT.toUpperCase().match(/../g).join(':')), []);
});

test('first publish writes the versioned APK then the manifest', async () => {
  const storage = memoryStorage();
  const entry = await run(storage, 34);
  assert.equal(versionedPathname('tany', '0.1.0-staging', 34), 'android/tany/tany-0.1.0-34.apk');
  assert.ok(storage.blobs.has('android/tany/tany-0.1.0-34.apk'));
  const manifest = JSON.parse(storage.blobs.get('android/tany/latest.json').body);
  assert.deepEqual(manifest, entry);
  assert.equal(manifest.versionName, '0.1.0');
  assert.equal(manifest.commit, '7d2e3ec');
  assert.equal(manifest.sha256, sha256(Buffer.from('apk-34')));
  assert.equal(manifest.publishedAt, '2026-10-07T12:32:00.000Z');
});

test('a same or lower versionCode never replaces the published build', async () => {
  const storage = memoryStorage();
  await run(storage, 34);
  await assert.rejects(run(storage, 34), /not greater than the published 34/);
  await assert.rejects(run(storage, 12), /not greater/);
  assert.equal(JSON.parse(storage.blobs.get('android/tany/latest.json').body).versionCode, 34);
});

test('upload failure leaves the previous manifest untouched', async () => {
  const storage = memoryStorage();
  await run(storage, 34);
  const failing = memoryStorage({ failPut: (p) => p.endsWith('.apk') });
  failing.blobs.set('android/tany/latest.json', storage.blobs.get('android/tany/latest.json'));
  await assert.rejects(run(failing, 35), /upload failed/);
  assert.equal(JSON.parse(failing.blobs.get('android/tany/latest.json').body).versionCode, 34);
});

test('corrupted upload (SHA-256 mismatch) never reaches the manifest', async () => {
  const storage = memoryStorage();
  await run(storage, 34);
  const realPut = storage.put;
  storage.put = (p, body, opts) => realPut(p, p.endsWith('.apk') ? Buffer.from('tampered') : body, opts);
  await assert.rejects(run(storage, 35), /SHA-256 mismatch/);
  assert.equal(JSON.parse(storage.blobs.get('android/tany/latest.json').body).versionCode, 34);
});

test('TANY and TANY Collect never touch each other', async () => {
  const storage = memoryStorage();
  await run(storage, 34);
  await publish({
    appKey: 'collect', apk: Buffer.from('collect-28'), info: info(28, 'ma.tany.collect.staging'), signers, expectedCert: CERT,
    commit: '41a8a50', storage, log: quiet,
  });
  assert.equal(JSON.parse(storage.blobs.get('android/tany/latest.json').body).versionCode, 34);
  assert.equal(JSON.parse(storage.blobs.get('android/collect/latest.json').body).name, 'TANY Collect');
  assert.ok(storage.blobs.has('android/collect/tany-collect-0.1.0-28.apk'));
});

test('concurrent manifest change is detected (ETag)', async () => {
  const storage = memoryStorage();
  await run(storage, 34);
  const realHead = storage.head;
  storage.head = async (p) => ({ ...(await realHead(p)), etag: 'stale' });
  await assert.rejects(run(storage, 35), /precondition failed/);
});

test('keeps the N most recent APKs and always the published one', async () => {
  const storage = memoryStorage();
  for (const code of [30, 31, 32, 33, 34]) await run(storage, code, { keep: 3 });
  const apks = [...storage.blobs.keys()].filter((p) => p.endsWith('.apk')).sort();
  assert.deepEqual(apks, ['android/tany/tany-0.1.0-32.apk', 'android/tany/tany-0.1.0-33.apk', 'android/tany/tany-0.1.0-34.apk']);
  const blobs = ['tany-0.1.0-9.apk', 'tany-0.1.0-10.apk', 'latest.json'].map((f) => ({ pathname: `android/tany/${f}`, url: f }));
  assert.deepEqual(selectPrunable(blobs, 'tany', 1, 'android/tany/tany-0.1.0-10.apk').map((b) => b.url), ['tany-0.1.0-9.apk']);
  assert.deepEqual(selectPrunable(blobs, 'tany', 0, 'android/tany/tany-0.1.0-10.apk').map((b) => b.url), ['tany-0.1.0-9.apk']);
});
