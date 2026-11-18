# MovieBox (Android)

A native Android app port of **MovieBox-Tui** — no Termux, no terminal setup, no external players required. Users install a single APK and get the full MovieBox experience with a premium streaming-app interface.

## Download the release APK (daily use)

A **signed release APK** is built by GitHub Actions on every push and published to the
`latest` release (stable signature — updates install in-place, no reinstall):

- **Release page:** <https://github.com/Rocker14427c/MovieBox-Tui/releases/tag/latest>
- **Direct APK:** <https://github.com/Rocker14427c/MovieBox-Tui/releases/download/latest/app-release.apk>

Install it by enabling **"Install unknown apps"** for your browser, then opening the downloaded APK.

## What it does

| Feature | Status |
| --- | --- |
| Movies & TV shows browsing | ✅ via the same MovieBox (oneroom) backend the TUI uses |
| Search | ✅ full-text search with grid results |
| Streaming | ✅ in-app ExoPlayer with all available mirrors |
| Live TV | ✅ M3U playlist import with channel grid |
| Favorites & Continue Watching | ✅ stored locally on-device |
| Premium UI | ✅ dark Material 3 theme, hero banners, poster rails |

## Build

**One-shot script** (installs the Android SDK and builds the APK automatically):

```bash
./build.sh            # debug APK
./build.sh --release  # release APK (needs signing config)
```

This checks for a JDK, downloads the Android command-line tools + `platform-35` + `build-tools`, accepts licenses, and runs `./gradlew assembleDebug`.

**Manually** — open the `android/` folder in **Android Studio** (Hedgehog or newer) and let it sync, then run `./gradlew assembleDebug`. Or from the command line with the SDK on your machine:

```bash
cd android
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK (needs signing config)
```

The resulting APK is at `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Streaming: how real links are resolved

The MovieBox mobile `play-info` endpoint currently returns only a site trailer, so the app resolves playable streams in this proven order (matching the fixed TUI provider):

1. **Subject detector links (movies):** `resourceDetectors[].resolutionList[]` from the details endpoint carries signed CDN MP4s. Mirrors use the MovieBox app User-Agent only — the CDN rejects a `Referer`.
2. **H5 web play (series):** the mobile API returns the trailer and series detectors have an empty `resolutionList`, so the app calls `https://h5.aoneroom.com/wefeed-h5-bff/web/subject/play?subjectId=...&se=...&ep=...` with the subject's `detailUrl` slug as the `Referer`. This returns the real signed MP4s. Mirrors carry the `officialmoviebox.com` `Referer` + User-Agent (the H5 CDN answers `429` without it).
3. **Legacy fallback:** `play-info` / `resource` endpoints, with the deprecation/trailer clip filtered out.

The deprecation-notice filter (`isTrailerUrl`) drops the known placeholder hashes and `macdn.aoneroom.com/other/` notice clips so a trailer is never offered as a playable stream.

## How it works

- `data/MovieBoxSigner.kt` — Kotlin port of the MovieBox request-signing scheme (HMAC-MD5 `x-tr-signature`, `x-client-token`, `x-client-build`, spoofed client info), identical to the TUI backend.
- `data/MovieBoxApi.kt` — catalog, search, detail, stream and subtitle endpoints with host failover, mirroring `src/providers/moviebox/` in the Rust TUI.
- `data/M3U.kt` — live TV playlist parser.
- `data/LocalStore.kt` — on-device history/favorites/settings persistence (zero telemetry, same privacy stance as the TUI).
- `ui/` — Compose UI: Home (hero + rails), Search, Detail (streams + seasons + favorites), Player (ExoPlayer), Live, Library.

## Disclaimer

The app does not host or store media. It plays publicly available streams; users must comply with local laws.
