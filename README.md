# OBD Insight

An Android app that connects to a Carista (or any ELM327-compatible) Bluetooth OBD2 dongle,
scans and records everything it sends and receives - raw hex, ASCII, decoded PIDs - and, for
data it doesn't recognize, asks Claude (with web search enabled) to reason about what sensor or
metric it's most likely reporting, using an Audi A1 8X 1.6 TDI (engine code CAYC) as the default
vehicle context.

## Why this exists

Carista and most consumer OBD2 dongles speak a superset of the standard OBD-II PID set: the
universal SAE J1979 "Mode 01" parameters (RPM, coolant temp, MAF, etc.) plus manufacturer-specific
Mode 22 `ReadDataByIdentifier` codes that vary by ECU and are rarely publicly documented. This app
decodes everything it can with a built-in standard-PID table, and for everything else it captures
the raw exchange and offers an AI + web-search lookup instead of guessing.

## Architecture

```
bluetooth/   Classic Bluetooth (SPP) and BLE transports behind a common ObdTransport interface,
             plus a device scanner for both paired-classic and nearby-BLE devices.
obd/         ELM327 AT-command session handling, the SAE J1979 standard PID table + decoder,
             DTC (trouble code) decoding, and the scan engine that walks supported PIDs.
data/        Room database: every raw TX/RX frame, every decoded/unknown PID exchange, and
             every AI analysis result, scoped to a connect-to-disconnect "session".
ai/          Calls the Anthropic API directly from the device (Java SDK) with the web_search
             tool enabled, using a user-supplied ("bring your own key") API key stored via an
             Android Keystore-backed EncryptedSharedPreferences file.
core/        DiagnosticsController - the one place that owns the active connection, session,
             and scan engine; every screen goes through it rather than touching Bluetooth or
             the database directly.
ui/          Jetpack Compose screens: Connect, Scan (live dashboard + custom probe), Log (raw
             frame viewer + CSV export), AI analysis, Settings.
export/      CSV export of a session's raw frames and decoded PID exchanges, shared via
             FileProvider/Intent.ACTION_SEND_MULTIPLE.
```

## The Carista Bluetooth protocol

Carista's dongle communicates over **Bluetooth Low Energy** (confirmed - its own app connects to
iOS over BLE, and iOS has no classic-SPP support). Carista's exact GATT service/characteristic
UUIDs are not publicly documented (its app is closed-source and no public teardown was found at
the time this was written). In practice, nearly every consumer BLE OBD2 dongle wraps the same
ELM327 AT-command byte stream inside one of a handful of well-known "BLE serial bridge" GATT
profiles, so `BleTransport` tries each of these in turn (see `bluetooth/BleUuids.kt`) and falls
back to generic characteristic discovery (first writable + first notifiable characteristic found
anywhere on the device) if none match. If your specific dongle doesn't connect, the raw log
screen will still show you what GATT interaction did happen, which is the fastest way to add its
actual profile to the known list.

A classic-Bluetooth (SPP) transport is also included, since many other ELM327 clones - and
possibly other Carista hardware revisions - support it, and it's a simpler, better-understood
protocol to fall back to.

## The AI PID/DID identification feature

This is the "look up on the internet and use AI to reasonably determine what sensors and metrics
are being returned" part of the brief. For any request/response pair that isn't a standard J1979
Mode 01 PID, the AI Analysis tab lets you ask Claude Opus (with the `web_search` tool enabled) to:

1. Consider the vehicle context (Audi A1 8X, engine code CAYC, editable in Settings).
2. Search community reverse-engineering resources where relevant (Ross-Tech/VCDS references,
   TunerPro/RomRaider definitions, GitHub UDS DID lists, VAG EA189/EA288 TDI forum write-ups).
3. Reason about the byte pattern in the captured response.
4. Return a best-effort hypothesis (name, unit, formula) with an explicit confidence level and
   cited sources - never presented as more certain than it is.

**This calls the Anthropic API directly from the device using your own API key** - there is no
backend server. That's a deliberate bring-your-own-key design (no server to run or trust), but it
does mean the key lives on your device. It's stored encrypted via the Android Keystore
(`ai/SecurePrefs.kt`) and excluded from Android auto-backup, and is used only for direct HTTPS
calls this device makes to `api.anthropic.com` - never sent anywhere else. Get a key at
[console.anthropic.com](https://console.anthropic.com).

## Building

Requires Android Studio (or the Android SDK + build tools) and a Gradle wrapper. This project was
put together in a sandboxed environment with **no access to the Android SDK or to
dl.google.com/services.gradle.org**, so:

- It has **not** been compiled or run - it hasn't been possible to verify it builds cleanly.
  Please treat it as a strong starting point that needs a normal Android Studio open-and-sync
  pass, not a verified-working build.
- The Gradle wrapper jar (`gradle/wrapper/gradle-wrapper.jar`) could not be downloaded, so it's
  not included. Open the project in Android Studio (it will offer to add the wrapper
  automatically), or run `gradle wrapper` yourself once you have a local Gradle install.

Minimum SDK 26, target/compile SDK 34, Kotlin + Jetpack Compose, Room for storage.

## Known limitations / next steps

- The standard PID table covers the common ~30 SAE J1979 Mode 01 parameters; it doesn't include
  every possible PID, and Mode 09 (vehicle info, e.g. VIN) isn't decoded yet.
- "Deep scan" of manufacturer-specific DIDs is deliberately not pre-seeded with a guessed list of
  CAYC-specific codes - no reliably-sourced public list of them was found, and shipping guesses
  as if they were confirmed would be misleading. Instead, probe candidates manually (Scan tab) or
  let the AI analyzer suggest ones worth trying, and they'll show up in the log either way.
- BLE profile auto-detection covers the common "BLE serial bridge" patterns (see `BleUuids.kt`)
  plus a generic fallback; a dongle using a genuinely novel profile may still fail to connect.
- No automated tests yet.
