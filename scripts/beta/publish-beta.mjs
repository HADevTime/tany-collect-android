#!/usr/bin/env node
// Publishes one STAGING APK to the TANY Android beta storage (Vercel Blob store behind https://beta.tany.ma).
//
// Same file in tany-android and tany-collect-android (keep them identical). Called by the `publish-beta` CI job
// on main only, after every test passed. Order = failure safety:
//   1. inspect the signed APK (package, versionCode/Name, signing certificate) — refuse anything that is not a
//      STAGING build signed with the expected stable STAGING key;
//   2. refuse a versionCode that is not strictly greater than the one currently published (Android would refuse
//      the update);
//   3. upload the versioned APK (never overwrites), download it back and check its SHA-256;
//   4. ONLY THEN replace `android/<app>/latest.json` (a single object write, conditional on the ETag read in 2 —
//      the atomic switch). Any failure before that leaves the previous version published and downloadable;
//   5. prune old versioned APKs (best effort, never the published one).
// Each app writes ONLY its own `android/<app>/latest.json`: TANY and TANY Collect can never overwrite each other.
//
// Env: BLOB_READ_WRITE_TOKEN, BETA_APP (tany | collect), BETA_APK, BETA_EXPECTED_CERT_SHA256, BETA_COMMIT,
//      BETA_KEEP (optional, default 5), ANDROID_HOME (build-tools: aapt2 + apksigner).

import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

export const APPS = {
  tany: { name: 'TANY', packageName: 'ma.tany.client.staging', filePrefix: 'tany' },
  collect: { name: 'TANY Collect', packageName: 'ma.tany.collect.staging', filePrefix: 'tany-collect' },
};

const APK_TYPE = 'application/vnd.android.package-archive';
const MANIFEST_MAX_AGE_SECONDS = 60; // Blob CDN minimum: a new build is visible within a minute.

export function sha256(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}

export function normalizeFingerprint(value) {
  return String(value ?? '').replace(/[^0-9a-f]/gi, '').toLowerCase();
}

/** `aapt2 dump badging` first line: package: name='…' versionCode='…' versionName='…' … */
export function parseBadging(output) {
  const line = output.split('\n').find((l) => l.startsWith('package:')) ?? '';
  const field = (key) => line.match(new RegExp(`${key}='([^']*)'`))?.[1];
  return { packageName: field('name'), versionCode: Number(field('versionCode')), versionName: field('versionName') };
}

/** `apksigner verify --print-certs`: one signer only, returns its certificate SHA-256 + DN. */
export function parseSigners(output) {
  const digests = [...output.matchAll(/Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)/gi)].map((m) => m[1]);
  const dn = output.match(/Signer #\d+ certificate DN: (.*)/)?.[1] ?? '';
  return { digests: digests.map(normalizeFingerprint), dn };
}

/** "0.1.0-staging" → "0.1.0" (the page shows the product version; the variant is implicit on beta.tany.ma). */
export function displayVersion(versionName) {
  return String(versionName).replace(/-staging$/, '');
}

export function versionedPathname(appKey, versionName, versionCode) {
  const { filePrefix } = APPS[appKey];
  const safe = displayVersion(versionName).replace(/[^0-9A-Za-z.]/g, '-');
  return `android/${appKey}/${filePrefix}-${safe}-${versionCode}.apk`;
}

export function manifestPathname(appKey) {
  return `android/${appKey}/latest.json`;
}

/** Checks the inspected APK against the app; returns the list of problems (empty = publishable). */
export function validateApk(appKey, info, signers, expectedCert) {
  const app = APPS[appKey];
  const problems = [];
  if (!app) return [`unknown app "${appKey}"`];
  if (info.packageName !== app.packageName) problems.push(`package ${info.packageName} ≠ ${app.packageName} (only STAGING builds are published)`);
  if (!Number.isInteger(info.versionCode) || info.versionCode < 2) problems.push(`invalid versionCode ${info.versionCode}`);
  if (!info.versionName) problems.push('missing versionName');
  if (signers.digests.length !== 1) problems.push(`expected exactly one signer, got ${signers.digests.length}`);
  if (/CN=Android Debug/i.test(signers.dn)) problems.push('APK is signed with a debug key');
  const expected = normalizeFingerprint(expectedCert);
  if (expected.length !== 64) problems.push('BETA_EXPECTED_CERT_SHA256 is missing or not a SHA-256 fingerprint');
  else if (signers.digests[0] !== expected) problems.push(`signing certificate ${signers.digests[0]} ≠ expected stable STAGING key ${expected}`);
  return problems;
}

export function buildEntry({ appKey, info, commit, publishedAt, downloadUrl, sha, size }) {
  return {
    name: APPS[appKey].name,
    packageName: info.packageName,
    versionName: displayVersion(info.versionName),
    versionCode: info.versionCode,
    commit: String(commit ?? '').slice(0, 7),
    publishedAt,
    downloadUrl,
    sha256: sha,
    size,
  };
}

/** Versioned APKs to delete: everything but the `keep` highest versionCodes and the published one. */
export function selectPrunable(blobs, appKey, keep, publishedPathname) {
  const re = new RegExp(`^android/${appKey}/${APPS[appKey].filePrefix}-.+-(\\d+)\\.apk$`);
  const apks = blobs
    .map((b) => ({ ...b, code: Number(b.pathname.match(re)?.[1]) }))
    .filter((b) => Number.isInteger(b.code))
    .sort((a, b) => b.code - a.code);
  return apks.slice(keep).filter((b) => b.pathname !== publishedPathname);
}

/**
 * Publishes `apk` (Buffer). `storage` = { head(pathname), put(pathname, body, opts), list(prefix), del(urls), fetch(url) }
 * so the flow can be tested without the network. Returns the new manifest entry.
 */
export async function publish({ appKey, apk, info, signers, expectedCert, commit, keep = 5, storage, now = new Date(), log = console.log }) {
  const problems = validateApk(appKey, info, signers, expectedCert);
  if (problems.length) throw new Error(`Refusing to publish:\n - ${problems.join('\n - ')}`);

  // Current published version (if any): the new build must be a valid Android update of it.
  const manifestPath = manifestPathname(appKey);
  const current = await storage.head(manifestPath);
  if (current) {
    const res = await storage.fetch(`${current.url}?v=${Date.now()}`);
    if (!res.ok) throw new Error(`Cannot read the published manifest (${res.status}) — nothing changed`);
    const published = await res.json();
    if (Number(published.versionCode) >= info.versionCode) {
      throw new Error(`versionCode ${info.versionCode} is not greater than the published ${published.versionCode} — nothing changed`);
    }
    log(`Published: ${published.versionName} (${published.versionCode}) → new: ${info.versionCode}`);
  } else {
    log('No version published yet for this app.');
  }

  // Versioned APK: never overwritten (a versionCode is published once).
  const sha = sha256(apk);
  const apkPath = versionedPathname(appKey, info.versionName, info.versionCode);
  const uploaded = await storage.put(apkPath, apk, { contentType: APK_TYPE, allowOverwrite: false });
  log(`Uploaded ${apkPath}`);

  // Integrity: what testers will download must be exactly what CI built and signed.
  const check = await storage.fetch(uploaded.url);
  if (!check.ok) throw new Error(`Uploaded APK not downloadable (${check.status}) — manifest unchanged`);
  const remoteSha = sha256(Buffer.from(await check.arrayBuffer()));
  if (remoteSha !== sha) throw new Error(`Uploaded APK SHA-256 mismatch (${remoteSha} ≠ ${sha}) — manifest unchanged`);

  // Atomic switch: one object write, conditional on the version we validated against.
  const entry = buildEntry({
    appKey, info, commit, publishedAt: now.toISOString(), downloadUrl: uploaded.url, sha, size: apk.length,
  });
  await storage.put(manifestPath, JSON.stringify(entry, null, 2), {
    contentType: 'application/json',
    cacheControlMaxAge: MANIFEST_MAX_AGE_SECONDS,
    ...(current ? { ifMatch: current.etag } : { allowOverwrite: false }),
  });
  log(`Manifest updated: ${entry.name} ${entry.versionName} (${entry.versionCode}) sha256=${sha}`);

  // Retention (best effort: a failure here never affects the published version).
  try {
    const prunable = selectPrunable(await storage.list(`android/${appKey}/`), appKey, keep, apkPath);
    if (prunable.length) {
      await storage.del(prunable.map((b) => b.url));
      log(`Pruned ${prunable.map((b) => b.pathname).join(', ')}`);
    }
  } catch (error) {
    log(`::warning::Pruning old APKs failed (published version unaffected): ${error.message}`);
  }
  return entry;
}

function buildTool(name) {
  const root = join(process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT ?? '', 'build-tools');
  const versions = readdirSync(root).sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  if (!versions.length) throw new Error(`No Android build-tools under ${root}`);
  return join(root, versions.at(-1), name);
}

async function blobStorage(token) {
  const { put, head, list, del, BlobNotFoundError } = await import('@vercel/blob');
  return {
    async head(pathname) {
      try {
        return await head(pathname, { token });
      } catch (error) {
        if (error instanceof BlobNotFoundError) return null;
        throw error;
      }
    },
    put: (pathname, body, opts) => put(pathname, body, { access: 'public', addRandomSuffix: false, token, ...opts }),
    async list(prefix) {
      const blobs = [];
      let cursor;
      do {
        const page = await list({ prefix, cursor, token });
        blobs.push(...page.blobs);
        cursor = page.hasMore ? page.cursor : undefined;
      } while (cursor);
      return blobs;
    },
    del: (urls) => del(urls, { token }),
    fetch: (url) => fetch(url, { cache: 'no-store' }),
  };
}

async function main() {
  const env = process.env;
  for (const key of ['BLOB_READ_WRITE_TOKEN', 'BETA_APP', 'BETA_APK', 'BETA_EXPECTED_CERT_SHA256', 'BETA_COMMIT']) {
    if (!env[key]) throw new Error(`Missing ${key}`);
  }
  const info = parseBadging(execFileSync(buildTool('aapt2'), ['dump', 'badging', env.BETA_APK], { encoding: 'utf8' }));
  const signers = parseSigners(execFileSync(buildTool('apksigner'), ['verify', '--print-certs', env.BETA_APK], { encoding: 'utf8' }));
  const entry = await publish({
    appKey: env.BETA_APP,
    apk: readFileSync(env.BETA_APK),
    info,
    signers,
    expectedCert: env.BETA_EXPECTED_CERT_SHA256,
    commit: env.BETA_COMMIT,
    keep: Number(env.BETA_KEEP ?? 5),
    storage: await blobStorage(env.BLOB_READ_WRITE_TOKEN),
  });
  if (env.GITHUB_STEP_SUMMARY) {
    const { appendFileSync } = await import('node:fs');
    appendFileSync(env.GITHUB_STEP_SUMMARY, `### ${entry.name} β publié sur beta.tany.ma\n\n` +
      `- Version ${entry.versionName} · build ${entry.versionCode} · commit \`${entry.commit}\`\n- SHA-256 \`${entry.sha256}\`\n`);
  }
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  main().catch((error) => {
    console.error(`::error::${error.message}`);
    process.exit(1);
  });
}
