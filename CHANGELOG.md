# 📜 Changelog

All notable changes to **Xmd** are documented in this file.
The format loosely follows [Keep a Changelog](https://keepachangelog.com/), and versioning follows [SemVer](https://semver.org/) with pre-release identifiers (`-beta.N`, `-rc.N`, ...) leading up to `1.0.0`.

## [1.0.0-beta.2] - 2026-08-24

### ✨ Added
- 🧲 **Built-in torrent support** — download magnet links and `.torrent` files directly in-app, with a dedicated Add Torrent confirmation dialog and magnet icon. Torrents can also be added from links found while browsing.
- ▶️ **YouTube downloads via yt-dlp** — new `full` build flavor bundles yt-dlp + ffmpeg for a quality-picker download flow; the existing `lite` flavor (FuckingFast/direct/fitgirl/torrent only) stays unchanged in size and dependencies. Both flavors split per-ABI (`arm64-v8a`, `armeabi-v7a`), for four release APKs total.
- 🔄 **yt-dlp Update controls** — manual Update button and a Stable/Nightly release-channel switch in Settings; the 24h background auto-update now follows the saved channel instead of a hardcoded one.
- 🖼️ **Proper audio thumbnails** for yt-dlp downloads, plus an **Open** button on finished downloads and download stats for YouTube items.
- 📋 **YouTube clipboard detection**, Google-powered address bar suggestions, a bookmark star toggle, and website import; default bookmarks removed from fresh installs.
- 🪟 **Per-tab WebView pool** in the Browser for instant tab switching, cross-tab back history, and no stale history/old-page flash on a fresh tab.
- 🧭 **Chrome-style toolbar rework** — new tab / search / reload / tabs / overflow — fixing it from reappearing over content after a background-kill activity recreation.
- 🎨 **Material 3 Expressive redesign** across the entire UI — dialogs, switches, buttons, and layouts rebuilt with Material 3 components.
- 🌈 **5 built-in themes** — Default, Aurora, Nord, Dracula, and Catppuccin — with app colors migrated to theme attributes for consistent dynamic theming.

### 🛠 Fixed
- ⏯️ Manual Resume/Cancel now survive a killed process over long pauses; auto-retry defaults to **off**.
- 📉 Eliminated download list flicker on progress ticks and fixed the notification getting stuck on "Preparing" when paused.
- 🌀 Fixed tab-switch lag, cross-tab back-history handling, and old-page flash on Browser navigation.
- 🔐 Fixed a Material 3 `AlertDialog` theme crash and general theme-consistency polish.
- 🩹 Fixed an XML namespace typo (`file_paths.xml`) and an XML comment syntax error that were breaking CI builds.
- 🌐 DoH resolver concurrency fix for more reliable Private DNS resolution.

---

## [1.0.0-beta.1] - 2026-08-20

🚀 **First public pre-release** of Xmd — Xtreme Media Downloader.

### ✨ Added
- 📥 **Core downloader** — paste `fuckingfast.co` share links, `dl.fuckingfast.co` direct links, or a `fitgirl-repacks.site` page URL to build a download queue.
- 🛡️ In-app **WebView challenge screen** to clear Cloudflare/Turnstile verification when a share link requires it.
- ⏸️ **Resumable, pause/cancel-able downloads** running in a foreground service.
- 🗂️ **IDM-style auto-categorized downloads** — files sorted by extension into `Videos`, `Music`, `Documents`, `Apps`, or `Others` subfolders (with a Settings toggle to save flat into Downloads instead).
- 💾 Download queue **persists across app restarts** (Room-backed storage), with auto-resume of queued downloads and manual Start/Clear buttons.
- 🔁 **Auto-retry on network errors** (3 attempts, toggle in Settings).
- ⏰ IDM-style **expired-link popup** with one-tap retry, plus per-item and bulk retry-all/clear-all actions.
- 🔔 "Starting download" snackbar with a **VIEW** action.
- 🌐 New **Browser tab** — speed-dial bookmarks with real site favicons, in-app WebView browsing, and automatic download interception.
- 🔍 **Address bar** with DuckDuckGo search suggestions, tap-to-load, and quick-add bookmarks.
- 📜 **Browsing history** tab/overlay.
- ↔️ **Swipe gestures** to switch between bottom navigation tabs.
- 🔒 **Private DNS** setting for in-app browsing — AdGuard DNS-over-HTTPS (default), Off, or a Custom DoH endpoint.
- 🎨 **Rebranded** to Xmd — Xtreme Media Downloader, with a new adaptive app icon and package ID `com.invictus.xmd`.

### 🏗️ Infrastructure
- 🤖 CI builds a **signed release APK** via GitHub Actions (`apksigner`, secrets-based signing).
- 🏷️ Tagged stable releases (`vX.Y.Z`) publish a signed APK with a SHA-256 checksum to GitHub Releases.
- 🧪 Tagged **pre-releases** (`vX.Y.Z-beta.N`, `-rc.N`, ...) publish a signed, clearly-flagged pre-release build via a dedicated workflow.
- 🧹 CI lint checks, issue templates, and a CONTRIBUTING guide.

---
> ⚠️ **This is a pre-release build** — expect rough edges. Please [open an issue](../../issues) if you run into problems.
