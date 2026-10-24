#!/usr/bin/env bash
# Signature finale de l'APK avec rotation de clé (APK Signature Scheme v3).
#   - Android 8   : signature v1/v2 avec l'ancienne clé (les mises à jour continuent).
#   - Android 9+  : signature v3 avec la nouvelle clé + la lignée signing/lineage.bin,
#                   qui prouve que la nouvelle clé succède à l'ancienne.
# Usage : sign-release.sh <apk-entrée> <apk-sortie>
# Variables : UC_KEYSTORE / UC_STORE_PASSWORD / UC_KEY_ALIAS / UC_KEY_PASSWORD   (ancienne clé)
#             UC_NEW_KEYSTORE / UC_NEW_PASSWORD                                   (nouvelle clé, alias « undercover »)
set -euo pipefail
IN="$1"; OUT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)apksigner"
: "${UC_NEW_KEYSTORE:?nouvelle clé absente (secret UC_NEW_KEYSTORE_B64)}"
: "${UC_NEW_PASSWORD:?mot de passe de la nouvelle clé absent (secret UC_NEW_PASSWORD)}"
"$APKSIGNER" sign \
  --ks "$UC_KEYSTORE" --ks-pass env:UC_STORE_PASSWORD --ks-key-alias "$UC_KEY_ALIAS" --key-pass env:UC_KEY_PASSWORD \
  --next-signer \
  --ks "$UC_NEW_KEYSTORE" --ks-pass env:UC_NEW_PASSWORD --ks-key-alias undercover --key-pass env:UC_NEW_PASSWORD \
  --lineage "$HERE/lineage.bin" --rotation-min-sdk-version 28 \
  --out "$OUT" "$IN"
# Contrôle : la lignée doit être présente et la nouvelle clé active
"$APKSIGNER" verify --verbose --print-certs "$OUT" | grep -E "Verified using|Signer #1 certificate SHA-256|lineage" || true
"$APKSIGNER" verify --min-sdk-version 28 --print-certs "$OUT" | grep -q "8a7aa39b18c8e32385b3ddcc97168ee4f523239a92241af6b015505f0d43bcb3" \
  || { echo "::error::la nouvelle clé n'est pas active dans l'APK signé"; exit 1; }
