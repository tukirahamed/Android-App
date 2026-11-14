# HOCO Router Manager (v0.1.0, unbuilt, untested)

Native Android app (Kotlin, Jetpack Compose, Material 3) for a HOCO DQ11 pocket router.

## STATUS: read this first
* **Not compiled.** The environment that wrote this had no Android SDK or internet. Expect that the
  first build may need small fixes. Send me the Gradle error and I will correct it.
* **Never run against a real DQ11.** No public DQ11 API documentation was found. The router client
  *assumes* a ZTE-style `/goform/goform_get_cmd_process` API. If the DQ11 differs, the app will show
  "Unsupported by firmware / unknown API" rather than fake data. Use Settings > Diagnostics and send me
  the output so I can adapt `RouterClient.kt`.
* Everything shown comes from the router. Missing values show "Unavailable".

## Build the APK without Android Studio (GitHub Actions)
1. Create a free GitHub account and a **private** repository.
2. Upload the contents of this folder (including the hidden `.github` folder), branch `main`.
3. Open the **Actions** tab > "Build APK" > **Run workflow**. Wait ~5 minutes.
4. Open the finished run, download artifact **HocoRouterManager-debug-apk**, unzip it.
5. Copy `app-debug.apk` to the iQOO Z9 Turbo, tap it, and allow "install unknown apps".

## Build with Android Studio instead
Open this folder, let it sync (use Gradle 8.7 / JDK 17), then Build > Build APK(s).

## Implemented
Auto-detect Wi-Fi gateway (traffic bound to the Wi-Fi network, works without mobile internet),
manual IP override, login (encrypted password storage), dashboard (battery, signal, speed with
sparkline, totals, devices, uptime), device list, SMS list / mark read / delete (multi) / send,
reboot with confirmation, light/dark/system theme, refresh interval, background checks every ~15 min
(new SMS + low battery notifications, SMS text hidden by default), diagnostics export, Exit button.
Cleartext HTTP is allowed only for common router addresses (`network_security_config.xml`).

## Not implemented yet (needs the real DQ11 pages)
Wi-Fi SSID/password change, device blocking, APN, data limits, admin password change, factory reset,
home-screen widgets, biometric lock, daily/monthly statistics, signal/connection/device alerts.
Shutdown is not offered: no genuine shutdown command is known for this router.

## Field assumptions to verify on the real router
Battery `battery_vol_percent`/`battery_charging`; signal `signalbar`,`lte_rsrp`,`lte_snr`,`rssi`;
traffic `realtime_*_thrpt|bytes`; clients `station_list`; SMS `sms_data_total` (UCS-2 hex body),
`SET_MSG_READ`, `DELETE_SMS`, `SEND_SMS`; `REBOOT_DEVICE`; login `LOGIN` (base64 or plain password).
