# MovieBox (Android) — powered by Bingr.one

A native Android app for streaming movies & TV shows — **no Termux, no browser, no laggy
webview**. A single APK gives you a smooth, glassmorphic streaming experience backed by the
**Bingr.one** content API and played with a hardware-accelerated ExoPlayer.

## Download the release APK (daily use)

A **signed release APK** is built by GitHub Actions on every push and published to the
`latest` release (stable signature — updates install in-place, no reinstall):

- **Release page:** <https://github.com/Rocker14427c/MovieBox-Tui/releases/tag/latest>
- **Direct APK:** <https://github.com/Rocker14427c/MovieBox-Tui/releases/download/latest/app-release.apk>

Install it by enabling **"Install unknown apps"** for your browser, then opening the downloaded APK.

## What it does

| Feature | Status |
| --- | --- |
| Movies & TV shows browsing | ✅ curated home rails (Trending / New / Movies / TV) |
| Search | ✅ full-text search with grid results |
| Streaming | ✅ in-app ExoPlayer, watch-page stream resolution (movies + per-episode TV) |
| TV seasons & episodes | ✅ season/episode picker, per-episode streams |
| Favorites & Continue Watching | ✅ stored locally on-device |
| Premium UI | ✅ glassmorphic dark Material 3 theme |

## Why it's smooth on low-end devices

- **Native Compose + ExoPlayer** — no webview, no heavy JS. Video is decoded by the system
  media stack (hardware-accelerated), which is far smoother than a mobile website.
- **Lightweight glass** — frosted-glass surfaces use translucent fills + hairline borders,
  deliberately avoiding per-frame blur render-effects that cause jank on low-end phones.
- **Lazy lists** everywhere (`LazyRow` / `LazyVerticalGrid`) so only visible posters are composed.
- **Parallel stream resolution** with per-server timeouts — one slow server never stalls playback.

## How streaming works

The app talks directly to Bingr (`bingr.one` / `api.bingr.one`), the same source the
bingr.one web player uses:

- `GET /api/search?q=&page=` — full-text search (TMDB-backed metadata).
- `GET /api/details/{movie|tv}/{id}` — title, year, poster, backdrop, rating, overview,
  genres, seasons and "more like this".
- **Stream resolution** — Bingr's `POST /api/stream` endpoint is fronted by Cloudflare and
  hard-blocks non-browser clients (HTTP 403, TLS-fingerprint based; header spoofing does not
  bypass it). Instead the app fetches the **public watch page** (`GET`, returns 200), which
  server-renders the playable link, and extracts the embedded HLS bridge URL:
  - Movie: `GET /watch/movie/{id}`
  - TV: `GET /watch/tv/{id}/{season}/{episode}`
  The embedded URL is a Cloudflare-Workers HLS bridge wrapping a signed `master.m3u8`
  (`dash-hls-bridge…workers.dev/?url=…&Policy=…&Signature=…&Key-Pair-Id=…`), directly
  playable in ExoPlayer. The page embeds it both HTML-escaped (`&amp;`) and JS-escaped
  (`\u0026`); the app normalizes both to a clean URL.

No signing, no tokens — plain HTTP with a browser-like User-Agent.

## Build

**One-shot script** (installs the Android SDK and builds the APK automatically):

```bash
./build.sh            # debug APK
./build.sh --release  # release APK (uses the committed signing keystore)
```

**Manually** — open the `android/` folder in **Android Studio** (Hedgehog or newer) and let it
sync, then run `./gradlew assembleDebug`. Or from the command line with the SDK on your machine:

```bash
cd android
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK
```

The resulting APK is at `app/build/outputs/apk/debug/app-debug.apk`. Install it with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## How it works

- `data/BingrApi.kt` — HTTP client for the Bingr API: search, details, and watch-page
  stream resolution (extracts the playable HLS URL from the watch page).
- `data/LocalStore.kt` — on-device history/favorites persistence (zero telemetry).
- `data/Models.kt` — catalog / detail / stream data classes.
- `ui/` — Compose UI: Home (glass hero + rails), Search, Detail (seasons + streams +
  "more like this"), Player (ExoPlayer), Library.
- `ui/theme/Theme.kt` — dark glassmorphic color scheme.

## Disclaimer

The app does not host or store media. It plays publicly available streams; users must comply
with local laws.

<!-- trigger build -->
