# TmVpn

[![Build](https://github.com/seyit474/TmVpn/actions/workflows/build.yml/badge.svg)](https://github.com/seyit474/TmVpn/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/seyit474/TmVpn?include_prereleases)](https://github.com/seyit474/TmVpn/releases)

Xray çekirdeği üzerinde **VLESS / VMess / Shadowsocks** destekleyen, abonelik
tabanlı, otomatik en hızlı sunucuyu seçen Android VPN istemcisi.

## Özellikler

- 🔗 **Abonelik tabanlı** — sunucu listesi tek bir URL'den çekilir,
  base64 kodlu ve düz metin subscription formatları desteklenir
- ⚡ **Otomatik en hızlı sunucu** — tüm sunuculara paralel TCP handshake
  ölçümü yapılır, en düşük gecikmeli sunucu otomatik seçilir
- 🛡️ **VLESS REALITY** — pbk / sid / fp / sni / flow (xtls-rprx-vision) dahil
- 🌐 **VMess** (ws / grpc / tcp) ve **Shadowsocks** (modern + eski link formatı)
- 🎨 **Modern arayüz** — Jetpack Compose + Material 3, koyu tema,
  splash ekranı, canlı bağlantı durumu
- 🔔 **Kalıcı bildirim** — bağlantı durumu ve tek dokunuşla kesme aksiyonu
- 🚫 **Reklam engelleme** — `geosite:category-ads-all` yönlendirme kuralı

## Durum

| Bileşen | Durum |
|---|---|
| Subscription çekme + parse (VLESS/VMess/SS) | ✅ Hazır |
| Paralel TCP ping + en hızlı sunucu seçimi | ✅ Hazır |
| Compose UI (bağlan/kes, sunucu listesi, hata akışı) | ✅ Hazır |
| Xray JSON config üretimi | ✅ Hazır |
| VpnService (tun + bildirim + durum köprüsü) | ✅ Hazır |
| Unit testler (parser + config builder) | ✅ Hazır |
| **libXray çekirdeği (gerçek tünel)** | 🚧 Bekliyor |
| tun2socks entegrasyonu | 🚧 Bekliyor |

> Çekirdek paketlenmediği sürece uygulama dürüst davranır: BAĞLAN'a
> basıldığında tünel kurulmaz ve kullanıcıya açık bir hata gösterilir.
> "Sahte bağlı" durumu asla oluşmaz.

## Derleme

### GitHub Actions (önerilir)

Her `main` push'unda debug APK otomatik derlenir ve *Actions → Artifacts*
altına yüklenir. `v*` tag'i atıldığında imzalı release APK üretilir.

Gerekli repository secrets:

| Secret | Açıklama |
|---|---|
| `SUBSCRIPTION_URL` | Abonelik endpoint'i (zorunlu) |
| `KEYSTORE_BASE64` | Release keystore (base64) — imzalı APK için |
| `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | Keystore bilgileri |

### Lokal

```bash
# SUBSCRIPTION_URL'i ortam değişkeni ya da gradle property olarak ver
SUBSCRIPTION_URL="https://..." ./gradlew assembleDebug

# Unit testler
./gradlew testDebugUnitTest
```

## Mimari

```
┌─────────────────────────────────────────┐
│  Compose UI (MainActivity / HomeScreen) │
│        ↕ StateFlow                      │
│  VpnViewModel (UiState)                 │
└─────────────────────────────────────────┘
     ↓                  ↓            ↑
┌──────────────┐  ┌──────────────┐  │
│ Subscription │  │ ServerPinger │  │ VpnStateRepository
│   Fetcher    │  │  (TCP ping)  │  │ (servis → UI köprüsü)
└──────────────┘  └──────────────┘  │
     ↓                              │
  ConfigParser → List<ServerConfig> │
     ↓                              │
  XrayConfigBuilder → Xray JSON     │
     ↓                              │
  XrayVpnService ───────────────────┘
    ├── VpnService.Builder → tun fd
    ├── XrayCore (libXray) → SOCKS :10808   ← soyutlama hazır
    └── tun2socks: tun ↔ SOCKS :10808       ← entegrasyon bekliyor
```

## Çekirdek Entegrasyonu (Aşama 2)

Gerçek tünel için üç parça gerekiyor:

### 1. libXray.aar

**A) Hazır AAR (önerilir):**
`github.com/2dust/AndroidLibXrayLite` releases'ından AAR'ı indir,
`app/libs/` altına koy — gradle otomatik dahil eder.

**B) Kendin derle:**
```bash
git clone https://github.com/xtls/libxray
cd libxray
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
gomobile bind -target=android -androidapi=24 \
  -o libXray.aar github.com/xtls/libxray
```

### 2. tun2socks

`hev-socks5-tunnel` (önerilir) `.so` dosyalarını
`app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/` altına koy.

### 3. Adapter

`service/core/XrayCore.kt` içindeki arayüzü saran bir adapter yaz ve
`XrayCoreProvider.create()` içinde döndür — servis koduna dokunmak gerekmez:

```kotlin
class LibXrayCore : XrayCore {
    override val isAvailable = true
    override fun start(configJson: String) { Libv2ray.startLoop(configJson) }
    override fun stop() { Libv2ray.stopLoop() }
}
```

## Yol Haritası

- [ ] libXray.aar + tun2socks entegrasyonu (gerçek tünel)
- [ ] Trafik istatistikleri (upload / download)
- [ ] Kill switch
- [ ] Seçili sunucu ve son liste kalıcılığı (DataStore)
- [ ] Otomatik abonelik yenileme zamanlayıcısı
- [ ] Türkmence çeviri (`values-tk`)
- [ ] Per-app proxy (split tunneling)
