# TradeVision 📈

**Your Trading Companion — Android App**

TradeVision is a full-featured Android trading companion app that wraps a modern web-based trading interface in a native Android shell, providing seamless access to real-time market data, price alerts, and portfolio tracking.

---

## ✨ Features

- **Real-Time Price Streaming** — WebSocket-powered live price updates
- **Price Alerts** — Set custom alerts with push notification delivery
- **Watchlists** — Organize and track your favorite symbols
- **OHLCV Charts** — Interactive candlestick charts with multiple timeframes
- **Deep Linking** — Direct links to specific symbols, alerts, and screens
- **Push Notifications** — Firebase Cloud Messaging for instant alerts
- **Offline Support** — Cached data and graceful offline handling
- **Dark Mode** — Full system theme support (light/dark/system)
- **JS Bridge** — Seamless communication between native Android and the web app

## 🏗️ Architecture

```
com.tradevision/
├── TradeVisionApplication.kt     # App initialization, notification channels
├── MainActivity.kt               # WebView + JS Bridge + WebSocket
├── auth/
│   └── AuthManager.kt            # JWT token management & refresh
├── network/
│   ├── ApiClient.kt              # Retrofit API client with interceptors
│   └── models.kt                 # All data models (Symbol, Candle, Alert, etc.)
├── ws/
│   └── PriceWebSocket.kt         # WebSocket client with auto-reconnect
├── push/
│   ├── FcmService.kt             # Firebase push notification handler
│   └── BootReceiver.kt           # Background service restart on boot
└── util/
    └── WebViewClient.kt          # Custom WebView client with security
```

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |
| Networking | Retrofit 2 + OkHttp 4 |
| WebSocket | OkHttp WebSocket |
| Push Notifications | Firebase Cloud Messaging |
| Analytics | Firebase Analytics |
| Crash Reporting | Firebase Crashlytics |
| Async | Kotlin Coroutines |
| WebView | AndroidX WebKit |
| UI | Material Design 3 |
| Build | Gradle 8.5 + Kotlin DSL |

## 📦 Dependencies

### Core Android
- `androidx.core:core-ktx:1.12.0`
- `androidx.appcompat:appcompat:1.6.1`
- `com.google.android.material:material:1.11.0`

### Networking
- `com.squareup.retrofit2:retrofit:2.9.0`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `com.squareup.retrofit2:converter-gson:2.9.0`

### Firebase
- Firebase BOM `32.7.2`
- Firebase Messaging, Analytics, Crashlytics, Auth

### Data
- `androidx.datastore:datastore-preferences:1.0.0`

## 🚀 Getting Started

### Prerequisites

- **Android Studio Hedgehog** (2023.1.1) or later
- **JDK 17**
- **Android SDK 34**

### Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-org/tradevision-android.git
   cd tradevision-android
   ```

2. **Add `google-services.json`:**
   
   Download from [Firebase Console](https://console.firebase.google.com/) and place at:
   ```
   app/google-services.json
   ```

3. **Build & Run:**
   ```bash
   ./gradlew assembleDebug
   ```
   
   Or open in Android Studio and click **Run**.

### Release Build

1. Create keystore:
   ```bash
   keytool -genkey -v -keystore keystore/release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias tradevision -storepass <password>
   ```

2. Set environment variables:
   ```bash
   export KEYSTORE_FILE=keystore/release.jks
   export KEYSTORE_PASSWORD=<password>
   export KEY_ALIAS=tradevision
   export KEY_PASSWORD=<password>
   ```

3. Build release:
   ```bash
   ./gradlew assembleRelease
   ```

## 🔧 GitHub Actions CI/CD

The project includes a complete CI/CD pipeline in `.github/workflows/build-apk.yml`:

### Required Secrets

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded release keystore (`base64 -w 0 keystore/release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g., `tradevision`) |
| `KEY_PASSWORD` | Key password |

### Workflows

- **Push to `main`/`develop`** — Lint → Unit Test → Build Debug + Release APK
- **Pull Request** — Lint → Unit Test → Build Debug APK
- **Tag push (`v*`)** — Full build → Create GitHub Release with APK

### Creating a Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

This triggers the full pipeline and creates a GitHub Release with the APK attached.

## 🌐 API Configuration

The app connects to the TradeVision API backend:

| Environment | Base URL |
|------------|----------|
| **Production** | `https://api.tradevision.app/` |
| **Development** | `http://10.0.2.2:8080/` (Android emulator → host) |
| **WebSocket** | `wss://ws.tradevision.app/prices` |

## 🔒 Security

- **Network Security Config** — Enforces HTTPS, certificate pinning in production
- **Token Refresh** — Automatic JWT token refresh with 1-minute buffer
- **ProGuard/R8** — Code obfuscation and minification for release builds
- **Secure Storage** — SharedPreferences for token storage (consider EncryptedSharedPreferences for production)
- **No Cleartext** — Cleartext traffic only allowed for local development

## 📱 Push Notifications

TradeVision uses Firebase Cloud Messaging for:

- **Price Alerts** — High-priority with vibration
- **Order Notifications** — Fills, cancellations
- **News Alerts** — Market news and updates
- **System Messages** — App updates and announcements

Notification channels:
- `tradevision_channel` — Price alerts (high importance)
- `tradevision_general` — General notifications (default)
- `tradevision_websocket` — WebSocket service status (low)

## 📄 License

```
MIT License

Copyright (c) 2024 TradeVision

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">

**Built with ❤️ for traders, by traders.**

[![TradeVision](https://img.shields.io/badge/TradeVision-Android-green?style=for-the-badge&logo=android)](https://tradevision.app)

</div>
