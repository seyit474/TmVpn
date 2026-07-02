#!/usr/bin/env bash
# libv2ray.aar (AndroidLibXrayLite / Xray-core gomobile derlemesi) indirir.
#
# Bu ortam dışında (GitHub Actions gibi) çalıştırılmalıdır — derlenmiş AAR
# app/libs/ altına konur ve Gradle otomatik olarak gerçek çekirdek adapter'ını
# (src/xray) derlemeye alır. AAR yoksa proje stub ile derlenir.
set -euo pipefail

REPO="2dust/AndroidLibXrayLite"
DEST="app/libs/libv2ray.aar"
mkdir -p "$(dirname "$DEST")"

echo "En güncel $REPO sürümü sorgulanıyor..."
TAG="$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" \
  | grep -oP '"tag_name":\s*"\K[^"]+')"

if [ -z "${TAG:-}" ]; then
  echo "HATA: sürüm etiketi alınamadı" >&2
  exit 1
fi

URL="https://github.com/${REPO}/releases/download/${TAG}/libv2ray.aar"
echo "İndiriliyor: $TAG -> $DEST"
curl -fSL -o "$DEST" "$URL"

echo "Tamam:"
ls -la "$DEST"
