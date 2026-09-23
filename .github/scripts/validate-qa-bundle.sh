#!/usr/bin/env bash
# Checks that an (untrusted) PR-built AAB is a QA build we are willing to sign and upload.
# Usage: validate-qa-bundle.sh <bundle.aab> <expected-versionCode>
# Requires aapt2 on PATH or at $AAPT2 (preinstalled on GitHub's Ubuntu runners via the Android SDK).
set -euo pipefail

aab="$1"
expected_code="$2"
expected_package="com.tandemmoto.qa"
aapt2="${AAPT2:-aapt2}"

fail() { echo "::error::QA bundle rejected: $*" >&2; exit 1; }

[ -f "$aab" ] || fail "file not found: $aab"

# Only a single base module is allowed (no dynamic feature modules smuggling extra code/manifests).
extra=$(unzip -Z1 "$aab" | cut -d/ -f1 | sort -u | grep -vxE 'base|BUNDLE-METADATA|BundleConfig.pb|META-INF' || true)
[ -z "$extra" ] || fail "unexpected top-level entries: $(echo "$extra" | tr '\n' ' ')"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q -j "$aab" base/manifest/AndroidManifest.xml base/resources.pb -d "$work" \
  || fail "missing base manifest/resources"
# aapt2 can't read an AAB directly, but it reads a proto-format APK built from the base module.
(cd "$work" && zip -q manifest.apk AndroidManifest.xml resources.pb)
tree=$("$aapt2" dump xmltree "$work/manifest.apk" --file AndroidManifest.xml)

package=$(echo "$tree" | sed -nE 's/^ *A: package="([^"]+)".*/\1/p' | head -1)
code=$(echo "$tree" | sed -nE 's/.*:versionCode\(0x0101021b\)=([0-9]+).*/\1/p' | head -1)

[ "$package" = "$expected_package" ] || fail "package is '$package', expected '$expected_package'"
[ "$code" = "$expected_code" ] || fail "versionCode is '$code', expected '$expected_code'"
if echo "$tree" | grep -qE ':debuggable\(0x0101000f\)=(true|-1|0xffffffff)'; then
  fail "bundle is debuggable"
fi

echo "QA bundle OK: package=$package versionCode=$code"
