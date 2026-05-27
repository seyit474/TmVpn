# Telo VPN — Android VPN Uygulaması

Xray çekirdeği (VLESS / VMess / Shadowsocks) üzerinde çalışan,
**Marzban** paneliyle entegre, abonelik tabanlı Android VPN uygulaması.

## Özellikler

- **Marzban API Login** — Panel URL + kullanıcı adı/şifre ile giriş; token alınır, sunucu listesi otomatik çekilir
- **Otomatik en hızlı sunucu** — TCP ping ile paralel ölçüm
- **Xray-core** — VLESS (REALITY dahil), VMess, Shadowsocks desteği
- **Trafik istatistikleri** — Anlık upload/download hızı ve toplam kullanım
- **Kill Switch** — VPN kesildiğinde internet trafiğini tamamen engelle
- **Önyüklemede bağlan** — Cihaz açılışında otomatik VPN
- **Material 3 UI** — Koyu tema, animasyonlu bağlantı butonu

## Mimari

```
┌─────────────────────────────────────┐
│  Compose UI (Jetpack Navigation)    │
│  Home · Servers · Settings          │
└─────────────────────────────────────┘
          ↓
┌─────────────────────────────────────┐
│  MainViewModel (StateFlow)          │
└─────────────────────────────────────┘
     ↓            ↓            ↓
MarzbanRepo   ServerPinger   AppPrefs
     ↓              ↓       (DataStore)
 MarzbanApi   ConfigParser
  (OkHttp)
     ↓
  Sub URL → ServerConfig[]
     ↓
XrayConfigBuilder → JSON
     ↓
XrayVpnService:
  ├── VpnService.Builder → tun fd
  ├── XrayCoreEngine → Xray SOCKS :10808
  └── tun2socks: tun ↔ SOCKS
```

## Kurulum

### 1. libXray.aar

Gerçek VPN tüneli için gereklidir.

**Otomatik:** GitHub Actions `build.yml` AndroidLibXrayLite'ın son release'inden otomatik indirir.

**Manuel:**
```bash
curl -L -o app/libs/libXray.aar \
  https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar
```

Dosya yoksa uygulama stub engine ile derlenir (UI çalışır, gerçek tünel kurulmaz).

### 2. tun2socks

Tun arayüzündeki trafiği Xray'in SOCKS portuna yönlendirmek için:

```
app/src/main/jniLibs/
  ├── arm64-v8a/libhev-socks5-tunnel.so
  ├── armeabi-v7a/libhev-socks5-tunnel.so
  └── x86_64/libhev-socks5-tunnel.so
```

Kaynak: [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)

`XrayVpnService.kt` içindeki `startTun2Socks()` fonksiyonunu etkinleştir.

### 3. libXray JNI Bağlama

`XrayCoreEngine.kt` içindeki `LibXrayEngine` yorumlarını etkinleştir:

```kotlin
fun createXrayEngine(context: Context): XrayCoreEngine = LibXrayEngine(context)
```

## Derleme

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (keystore gerekli)
./gradlew assembleRelease
```

## GitHub Secrets

Release için gerekenler:

| Secret | Açıklama |
|--------|----------|
| `KEYSTORE_BASE64` | Base64 kodlanmış keystore dosyası |
| `KEYSTORE_PASSWORD` | Keystore şifresi |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key şifresi |

## Kullanım

1. Uygulamayı aç
2. Marzban panel URL'ini ve yönetici bilgilerini gir
3. Giriş yap → sunucu listesi otomatik yüklenir
4. BAĞLAN butonuna bas → VPN izni ver → bağlantı kurulur
5. Sunucular sekmesinden farklı sunucu seç
6. Ayarlar sekmesinden kill switch ve otomatik bağlantıyı yapılandır
