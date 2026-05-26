#!/usr/bin/env bash
# Places aaptcompiler-8.5.0 artifacts directly into the Gradle module cache,
# bypassing the network download that gets corrupted by the local ISP.
#
# Run once before building:  bash fix_gradle_cache.sh

set -e
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
BASE="$GRADLE_HOME/caches/modules-2/files-2.1/com.android.tools.build/aaptcompiler/8.5.0"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SCRIPT_DIR/gradle-libs/aaptcompiler-8.5.0"

echo "Installing aaptcompiler-8.5.0 into Gradle cache..."

install_file() {
  local hash="$1" file="$2" src="$3"
  local dest="$BASE/$hash"
  if [ -f "$dest/$file" ]; then
    echo "  [OK] $file already present"
  else
    mkdir -p "$dest"
    cp "$src/$file" "$dest/$file"
    echo "  [+] installed $file"
  fi
}

install_file "f6d9db27cf289f6426d4559297536e0ff1f778b9" "aaptcompiler-8.5.0.jar"    "$SRC/jar"
install_file "49752748de3f634505d1a0d26f06ffb6f88ab1ce" "aaptcompiler-8.5.0.pom"    "$SRC/pom"
install_file "d967366e17a219ac8fb3b8f426fa4552b5cf3f5e" "aaptcompiler-8.5.0.module" "$SRC/module"

# metadata descriptor — install for both known versions
for META_VER in 2.106 2.107 2.108; do
  META="$GRADLE_HOME/caches/modules-2/metadata-$META_VER/descriptors/com.android.tools.build/aaptcompiler/8.5.0/d4e342018b23d58be902a60e67105aa1"
  if [ -d "$(dirname "$META")" ]; then
    mkdir -p "$META"
    cp "$SRC/meta/descriptor.bin" "$META/descriptor.bin"
    echo "  [+] metadata-$META_VER descriptor installed"
  fi
done

echo "Done. Now run:  bash gradlew assembleDebug"
