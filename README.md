<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/xmd.png" width="140" height="140" />
</p>

<h1 align="center">Xmd — Xtreme Media Downloader</h1>

<p align="center">
  <b>An Android download manager for FuckingFast share links, with a built-in torrent client, yt-dlp-powered YouTube downloads, and a fast in-app browser.</b>
  <br>
  <i>An Android port of the original PyQt5 desktop downloader — no bloat, just downloads that resume.</i>
</p>

> [!IMPORTANT]
> **Pre-release (`v1.0.0-beta.4`)** — under active development, expect rough edges. See [CHANGELOG.md](CHANGELOG.md) for what's new.

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg" />
  <img src="https://img.shields.io/badge/License-AGPL_v3-blue.svg" />
  <img src="https://img.shields.io/github/v/release/Utsavrajputt/xmd.svg?logo=github&label=Release&cacheSeconds=3600" />
  <img src="https://img.shields.io/github/downloads/Utsavrajputt/xmd/total?logo=github&cacheSeconds=3600" />
</p>

<p align="center">
  <img src="https://img.shields.io/github/stars/Utsavrajputt/xmd?style=flat&logo=github&color=gold" />
  <img src="https://img.shields.io/github/forks/Utsavrajputt/xmd?style=flat&logo=github&color=blue" />
  <img src="https://img.shields.io/github/last-commit/Utsavrajputt/xmd?style=flat&logo=github" />
  <img src="https://img.shields.io/github/issues/Utsavrajputt/xmd?style=flat&logo=github&color=orange" />
</p>

<p align="center">
  <sub>⭐ • 🍴 • 🕓 • 🐛 &nbsp;—&nbsp; if Xmd's useful to you, a star helps more than you'd think</sub>
</p>

---

## Table of Contents

- [Features](#features)
- [Build Flavors](#build-flavors)
- [Project Structure](#project-structure)
- [Building](#building)
- [Releases](#releases)
- [Permissions](#permissions)
- [Contributing](#contributing)
- [License](#license)

---

## Features

<details open>
<summary><b>📥 Downloader</b></summary>

| Feature | Description |
|---|---|
| **Multi-source links** | Paste `fuckingfast.co` share links, `dl.fuckingfast.co` direct links, or a `fitgirl-repacks.site` page URL — source pages are expanded into their share links automatically |
| **Cloudflare/Turnstile handling** | In-app WebView challenge screen opens the share page so you can clear verification yourself; the direct URL is captured automatically once cleared |
| **Resumable downloads** | Pause/cancel-able downloads running in a foreground service, surviving app backgrounding and killed-process long pauses |
| **Auto-categorized downloads** | IDM-style sorting by extension into `Videos`, `Music`, `Documents`, `Apps`, or `Others` (or flat into Downloads via a Settings toggle) |
| **Retry system** | Auto-retry on network errors (off by default, toggle in Settings), an expired-link retry popup, and per-item/bulk retry & clear actions |
| **Persistent queue** | Room-backed download queue that survives app restarts, with auto-resume of queued items and manual Start/Clear controls |
| **Open & stats** | Open finished downloads directly from the list; download stats shown for YouTube items; tap the done/failed stats pill on Home to jump straight to that Downloads tab |
| **Share target** | A transparent `ShareReceiverActivity` handles links shared from other apps (e.g. Morphe) without ever bringing Xmd's main UI to the foreground — YouTube links get a quick bottom-sheet quality picker, everything else queues and starts silently |
| **Download on Wi-Fi Only** | Optional setting (off by default) that auto-pauses all live downloads when Wi-Fi drops and resumes them when it's back |
| **Default YouTube quality** | Pick "Ask always" or lock in a fixed quality to skip the picker sheet on every YouTube download |
| **Video preset & audio format** | Preferred container/codec/FPS ladder for quick quality picks, plus a default audio format (MP3, M4A, Opus, or Original) for Audio-only downloads |
| **Advanced quality section** | A real yt-dlp format probe surfaced as a collapsible list showing exact FPS/codec/file size per stream, alongside the standard ladder |

</details>

<details open>
<summary><b>🧲 Torrents</b></summary>

| Feature | Description |
|---|---|
| **Built-in torrent engine** | Magnet links and `.torrent` files download directly in-app — no external client needed |
| **Add Torrent dialog** | Dedicated confirmation dialog with its own magnet icon for adding torrents |
| **External torrent import** | Add torrents discovered while browsing the in-app Browser |

</details>

<details open>
<summary><b>▶️ YouTube (yt-dlp)</b></summary>

| Feature | Description |
|---|---|
| **yt-dlp-powered downloads** | YouTube links get a quality-picker flow backed by bundled yt-dlp + ffmpeg (`full` flavor only) |
| **Percent-based progress** | Separate progress/notification handling from byte-based downloads, since yt-dlp owns resolve + download + merge |
| **Update controls** | Manual Update button plus a Stable/Nightly release-channel switch in Settings, with 24h background auto-update following the saved channel |
| **Proper thumbnails** | Downloaded audio gets correct embedded thumbnail art |
| **Clipboard detection** | YouTube links copied to the clipboard are picked up automatically |

</details>

<details open>
<summary><b>🌐 Browser</b></summary>

| Feature | Description |
|---|---|
| **Speed-dial bookmarks** | Real site favicons (24h on-disk cache), star-to-bookmark toggle, and website import — no default bookmarks cluttering a fresh install |
| **Per-tab WebView pool** | Instant tab switching with no old-page flash, cross-tab back history, and stale history cleared on a fresh tab's first load |
| **Chrome-style toolbar** | Reworked toolbar (new tab / search / reload / tabs / overflow) that no longer reappears over content after a background-kill recreation |
| **Address bar suggestions** | Google-powered search suggestions merged with matching visited pages from history, each with a distinct icon |
| **Download interception** | Files opened in-app are caught and routed into the download queue automatically |
| **Swipe gestures** | Swipe to switch between browser tabs |
| **Media sniffing** | A `MediaSniffer` classifier detects HLS/DASH/direct media requests as pages load, surfacing a "videos found" chip and picker sheet for streamed media |
| **Private DNS** | Off (default), AdGuard, Google DNS, or Cloudflare (plain + adblock) DNS-over-HTTPS, or a Custom DoH endpoint — each option shows its DoH address, with concurrency fixes for reliable resolution |
| **Desktop site & Find in page** | Per-tab desktop UA + wide-viewport toggle, and a Chrome-style find-in-page overlay with prev/next navigation and a live match count |
| **Long-press menu** | Long-press a link or image for open-in-new-tab, download image, copy link address, or share link |
| **History** | Full browsing history tab/overlay with search by title/URL |

</details>

<details open>
<summary><b>🎨 Theme System</b></summary>

| Feature | Description |
|---|---|
| **Material 3 Expressive UI** | Full redesign across dialogs, switches, buttons, and layouts using Material 3 Expressive components |
| **5 Built-in Themes** | Default, Aurora, Nord, Dracula, and Catppuccin — colors migrated to theme attributes for consistent dynamic theming |
| **Polished assets** | Refreshed icons, tint work, a dedicated torrent magnet icon, and smoother celestial/placeholder styling throughout |

</details>

---

## Build Flavors

Xmd ships as two product flavors instead of one do-everything APK:

| Flavor | Description |
|---|---|
| `lite` | FuckingFast / direct / fitgirl / torrent downloads only — no yt-dlp or ffmpeg, smallest footprint |
| `full` | Everything in `lite`, plus YouTube downloads via bundled yt-dlp + ffmpeg (larger APK, since the Python/ffmpeg binaries yt-dlp needs can't be downloaded at runtime on Android 10+) |

Each flavor is additionally split per-ABI (`armeabi-v7a`, `arm64-v8a`), so a release produces four APKs total: `Xmd-lite-arm64-v8a`, `Xmd-lite-armeabi-v7a`, `Xmd-full-arm64-v8a`, `Xmd-full-armeabi-v7a`. There's no universal APK — every real device is one ABI or the other, so a combined build would just mean downloading native libraries you'll never use.

---

## Project Structure

```text
app/src/main/java/com/invictus/xmd/
├─ core/
│  ├─ LinkParser.kt              # share/direct/fitgirl/YouTube link parsing & validation
│  ├─ DownloadEngine.kt          # resumable streaming download engine
│  ├─ TorrentEngine.kt           # magnet/.torrent download engine
│  ├─ YtDlpManager.kt            # yt-dlp/ffmpeg wrapper (full flavor), no-op stub (lite flavor)
│  ├─ CategoryDetector.kt        # extension -> DownloadCategory mapping
│  ├─ QueueRepository.kt         # in-memory + Room-backed queue state
│  ├─ Settings.kt                # persisted app settings (DNS mode, retry, yt-dlp channel...)
│  ├─ DnsOverHttpsResolver.kt    # DoH resolver used by the in-app Browser
│  ├─ BookmarkRepository.kt, Bookmark.kt
│  ├─ HistoryRepository.kt, HistoryEntry.kt
│  ├─ FaviconLoader.kt, SuggestApi.kt
│  └─ db/                        # Room entities/DAOs (queue, bookmarks, history)
├─ service/
│  └─ DownloadService.kt         # foreground service driving downloads per category folder
├─ ui/
│  ├─ MainActivity.kt, HomeFragment.kt, DownloadsFragment.kt, QueueAdapter.kt
│  ├─ BrowserFragment.kt         # speed-dial, per-tab WebView pool, tabs, DNS settings
│  ├─ HistoryFragment.kt, HistoryAdapter.kt
│  ├─ BookmarkAdapter.kt, SuggestionAdapter.kt
│  ├─ theme/AppTheme.kt          # Default/Aurora/Nord/Dracula/Catppuccin theme definitions
│  └─ ChallengeActivity.kt       # WebView for clearing Cloudflare/Turnstile challenges
└─ FfApp.kt                      # Application class
```

---

## Building

Requires JDK 17 and the Android SDK (compileSdk 34, minSdk 26).

```bash
./gradlew assembleLiteDebug      # lite debug APK
./gradlew assembleFullDebug      # full (yt-dlp) debug APK
./gradlew assembleRelease        # unsigned release APKs, then sign with apksigner (see below)
```

Or open the project in Android Studio and run/build normally.

### Signing a release build

Release builds are intentionally unsigned by Gradle — `assembleRelease` produces unsigned APKs per flavor/ABI, which you sign explicitly with `apksigner`:

```bash
apksigner sign --ks your-release.jks --ks-key-alias <alias> \
  --out app-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

CI does this automatically on push to `main` via `.github/workflows/android-build.yml`, using repo secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`); signed APKs for all flavor/ABI combinations are uploaded as build artifacts.

---

## Releases

Two tag-triggered workflows build signed APKs and publish them to GitHub Releases with a SHA-256 checksum and notes pulled from `CHANGELOG.md`:

- **`.github/workflows/release.yml`** — stable releases, triggered by tags matching `vX.Y.Z` (e.g. `v1.0.0`).
- **`.github/workflows/prerelease.yml`** — pre-releases, triggered by tags matching `vX.Y.Z-suffix` (e.g. `v1.0.0-beta.1`, `v1.0.0-rc.2`). Published GitHub Releases are flagged **Pre-release** automatically.

To cut a release:

1. Bump `versionCode`/`versionName` in `app/build.gradle.kts` and add a matching `## [x.y.z]` (or `## [x.y.z-beta.N]`) section to the top of `CHANGELOG.md`, then commit and push those to `main`.
2. Tag the commit to match and push the tag:

   ```bash
   # stable
   git tag v1.0.0
   git push origin v1.0.0

   # pre-release
   git tag v1.0.0-beta.1
   git push origin v1.0.0-beta.1
   ```

3. The matching job runs automatically and publishes the GitHub Release with all four flavor/ABI APKs attached.

You can also trigger either workflow manually from the **Actions** tab → **Make release** / **Make pre-release** → **Run workflow**, entering the tag name without needing to push a tag first.

---

## Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — fetching links, resolving torrents/DoH, and downloading
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` — background download progress notification
- `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` — saving downloaded files into category subfolders

---

## Contributing

Pull requests are welcome. For bigger changes, open an issue first to discuss what you'd like to change, so effort isn't wasted on something that doesn't fit the project direction.

1. Fork the repo and create your branch from `main`
2. Keep commits focused and descriptively named
3. Test on a real device before opening the PR
4. Open a PR describing what changed and why

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

## License

Distributed under **GNU Affero General Public License v3.0 (AGPL-3.0-or-later)** — see [LICENSE](LICENSE).

Only download content you are authorized to access.

---

<div align="center">
  <sub>Built with ⚡ by <a href="https://github.com/Utsavrajputt">Utsav</a></sub>
</div>
