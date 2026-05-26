#!/usr/bin/env bash
# Installs bundled AGP 8.5.0 artifacts directly into the Gradle module cache,
# bypassing network downloads that get corrupted by the local ISP.
#
# Run BEFORE every build after clearing the module cache:
#   bash fix_gradle_cache.sh
#   bash gradlew assembleDebug --offline   (or without --offline for small deps)

set -e
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
FILES_CACHE="$GRADLE_HOME/caches/modules-2/files-2.1"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/gradle-libs/agp-cache"

if [ ! -d "$SRC" ]; then
    echo "ERROR: gradle-libs/agp-cache not found. Run from project root."
    exit 1
fi

echo "Installing bundled AGP artifacts into Gradle cache..."
count=0

# Walk src tree: SRC/<group>/<artifact>/<version>/<hash>/<file>
find "$SRC" -type f | while read -r src_file; do
    rel="${src_file#$SRC/}"          # e.g. com.android.tools.build/gradle/8.5.0/abc123/gradle-8.5.0.jar
    dest="$FILES_CACHE/$rel"
    dest_dir="$(dirname "$dest")"
    mkdir -p "$dest_dir"
    cp -f "$src_file" "$dest"        # -f always overwrites
    echo "  [+] $rel"
done

echo ""
echo "Done. Now build with:"
echo "  bash gradlew assembleDebug 2>&1 | tail -30"
