# Beta distribution — TANY Collect β (https://beta.tany.ma)

Testers install TANY Collect β (`ma.tany.collect.staging`, STAGING backend `https://staging.tany.ma`) from **https://beta.tany.ma**,
without GitHub access. Every merge to `main` publishes a new build that **updates** the installed app.
The page and its storage live in `tany-web/beta` (README there: Vercel project, Blob store, DNS).
PROD (`ma.tany.collect`, `release` build type, its signing and versioning) is **not** concerned.

## Pipeline (`.github/workflows/ci.yml`)

| Event | `android` job (build · lint · tests) | `publish-beta` job |
| --- | --- | --- |
| Pull request | ✅ | never |
| Push to `main` (merge) | ✅ | only if `android` passed |
| Other branch / manual run | ✅ (manual) | never |

`publish-beta` (environment `beta-staging`):
1. rebuilds `:app:assembleStaging` with the **stable STAGING key** (`-Ptany.staging.requireSigning=true`: the build
   fails without it — never a silent debug-signed build) and `versionCode = git rev-list --count HEAD`;
2. `scripts/beta/publish-beta.mjs` checks the APK with `aapt2`/`apksigner`: package `ma.tany.collect.staging`, one signer, not a
   debug key, certificate = `TANY_STAGING_CERT_SHA256`, versionCode **strictly greater** than the published one;
3. uploads `android/collect/tany-collect-<version>-<versionCode>.apk` (never overwritten), downloads it back and
   checks its SHA-256;
4. **only then** replaces `android/collect/latest.json` (one object write, conditional on the ETag read in 2);
5. keeps the 5 most recent APKs (best effort).

Any failure (tests, build, signing, upload, checksum) stops before step 4: the previous build stays published and
downloadable. This app only ever writes `android/collect/…`: TANY and TANY Collect cannot overwrite each other.
Two merges in a row are queued (`concurrency: publish-beta`), never cancelled mid-publish.

## Versioning

- STAGING `versionCode` = number of commits on `main` (monotonic: `main` is never rewritten), injected only into
  the `staging` variant (`androidComponents`, `tany.staging.versionCode`). `versionName` stays `defaultConfig`'s
  (+ `-staging`), shown on the page without the suffix.
- PROD `versionCode`/`versionName` (`defaultConfig`) are untouched.

## One-time setup (owner)

1. Create the STAGING key **once**, on your machine (written outside the repo):
   `scripts/beta/create-staging-keystore.sh tany-collect` — keep an offline backup of the `.p12` + password.
2. GitHub → this repo → Settings → Environments → **New environment `beta-staging`**, deployment branches:
   **Selected branches → `main`**. Add what the script prints:
   - secrets `TANY_STAGING_KEYSTORE_BASE64`, `TANY_STAGING_KEYSTORE_PASSWORD`, `TANY_STAGING_KEY_ALIAS`,
     `TANY_STAGING_KEY_PASSWORD`;
   - variable `TANY_STAGING_CERT_SHA256`;
   - secret `TANY_BETA_BLOB_TOKEN` = the read-write token of the Vercel Blob store `tany-android-beta`
     (`tany-web/beta/README.md`). Scoped to that store only — no database, no other bucket.
3. Merge to `main` (or re-run the last `main` CI run): the job summary shows the published version.

Never commit a keystore, a password or the Blob token. Never reuse this key for PROD (PROD gets its own key, Play
App Signing). Losing the STAGING key ⇒ testers uninstall the beta once and install the build signed with the new
one (update `TANY_STAGING_CERT_SHA256` at the same time).

## Testers

Builds published before this pipeline were signed with an ephemeral CI debug key: uninstall that old build **once**.
After that, each new build installs over the previous one (same key, higher `versionCode`), data kept.
