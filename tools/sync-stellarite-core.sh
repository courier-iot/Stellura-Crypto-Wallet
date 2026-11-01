#!/usr/bin/env bash
# Copies the Stellarite core (consensus rules, physics, mining engine, signing) from a
# Stellarite Miner checkout into stellarite-core/, so the wallet mines and signs with
# exactly the same code as the miner app.
#
#   tools/sync-stellarite-core.sh ../stellarite-miner
#
# Run it after every Stellarite release, then commit. Both coins' rules change with
# releases (a consensus change starts new chains), so an out-of-date copy would mine
# blocks the nodes reject.
set -euo pipefail
MINER="${1:?usage: tools/sync-stellarite-core.sh <path to stellarite-miner checkout>}"
SRC="$MINER/android/core/src"
[ -d "$SRC" ] || { echo "No android/core/src in $MINER (needs Stellarite Miner 12.1.0 or newer)"; exit 1; }
HERE="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$HERE/stellarite-core/src"
cp -R "$SRC" "$HERE/stellarite-core/src"
VERSION=$(grep -o 'versionName = "[^"]*"' "$MINER/android/app/build.gradle.kts" | cut -d'"' -f2)
{
  echo "Stellarite Miner $VERSION"
  echo "Synced $(date -u +%Y-%m-%d)"
  ( cd "$HERE/stellarite-core/src" && find . -type f | LC_ALL=C sort | xargs sha256sum )
} > "$HERE/stellarite-core/SOURCE"
echo "stellarite-core now matches Stellarite Miner $VERSION"
