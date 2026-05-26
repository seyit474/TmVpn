#!/usr/bin/env bash
# Downloads libv2ray.aar from AndroidLibXrayLite releases.
# Run once before building: bash setup_libs.sh

set -e

LIBV2RAY_VERSION="v26.5.19"
LIBV2RAY_URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/${LIBV2RAY_VERSION}/libv2ray.aar"
LIBS_DIR="$(dirname "$0")/app/libs"

mkdir -p "$LIBS_DIR"
echo "Downloading libv2ray.aar ${LIBV2RAY_VERSION}..."
curl -L --retry 3 --progress-bar -o "$LIBS_DIR/libv2ray.aar" "$LIBV2RAY_URL"
SIZE=$(stat -c%s "$LIBS_DIR/libv2ray.aar" 2>/dev/null || stat -f%z "$LIBS_DIR/libv2ray.aar")
echo "Done: $LIBS_DIR/libv2ray.aar  (${SIZE} bytes)"
