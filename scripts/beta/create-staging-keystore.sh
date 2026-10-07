#!/usr/bin/env bash
# Creates the DEDICATED, stable STAGING signing key of this app (beta.tany.ma) — run ONCE, on your own machine.
# Never the PROD key, never committed: the keystore is written OUTSIDE the repo; only its base64 goes to the GitHub
# environment `beta-staging` (docs/BETA_DISTRIBUTION.md). Keep an offline backup: losing it means every tester has to
# uninstall the beta once (Android refuses an update signed with another key).
#
# Usage: scripts/beta/create-staging-keystore.sh <app-label> [output-dir]   e.g. tany | tany-collect
set -euo pipefail
label="${1:?usage: $0 <app-label> [output-dir]}"
out_dir="${2:-$HOME/tany-staging-keys}"
alias_name="${label}-staging"
keystore="$out_dir/${label}-staging.p12"
mkdir -p "$out_dir" && chmod 700 "$out_dir"
[ -e "$keystore" ] && { echo "$keystore already exists — keep using it (a new key breaks updates)." >&2; exit 1; }

password="$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)"
keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$keystore" -alias "$alias_name" \
  -keyalg RSA -keysize 4096 -validity 10000 -storepass "$password" -keypass "$password" \
  -dname "CN=TANY ${label} STAGING (beta), O=TANY, C=MA" >/dev/null 2>&1
chmod 600 "$keystore"
fingerprint="$(keytool -list -v -storetype PKCS12 -keystore "$keystore" -alias "$alias_name" -storepass "$password" 2>/dev/null \
  | awk -F': ' '/SHA256:/ {print $2; exit}' | tr -d ':' | tr 'A-F' 'a-f')"
printf '%s' "$password" > "$out_dir/${label}-staging.password" && chmod 600 "$out_dir/${label}-staging.password"
base64 < "$keystore" | tr -d '\n' > "$out_dir/${label}-staging.p12.base64" && chmod 600 "$out_dir/${label}-staging.p12.base64"

cat <<INFO
Created $keystore (backup it offline, with $out_dir/${label}-staging.password).

GitHub → Settings → Environments → beta-staging:
  secret   TANY_STAGING_KEYSTORE_BASE64   = contents of $out_dir/${label}-staging.p12.base64
  secret   TANY_STAGING_KEYSTORE_PASSWORD = contents of $out_dir/${label}-staging.password
  secret   TANY_STAGING_KEY_PASSWORD      = same value as TANY_STAGING_KEYSTORE_PASSWORD (PKCS12)
  secret   TANY_STAGING_KEY_ALIAS         = $alias_name
  variable TANY_STAGING_CERT_SHA256       = $fingerprint
INFO
