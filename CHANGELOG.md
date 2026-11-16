# 📜 Changelog

All notable changes to **Xmd** are documented in this file.
The format loosely follows [Keep a Changelog](https://keepachangelog.com/), and versioning follows [SemVer](https://semver.org/) with pre-release identifiers (`-beta.N`, `-rc.N`, ...) leading up to `1.0.0`.

## [Unreleased]

### 🌐 Browser
- 🛡️ **New ad-blocking engine** — reads standard uBlock Origin / AdBlock Plus filter lists (the same syntax 1DM and Brave use): `||domain^`, wildcards, `$third-party`, `$script`/`$image`/… types, `$domain=`, `$important`, `$popup`, `@@` exceptions, `$badfilter`, regex rules, hosts files and plain domain lists. Requests are matched on URL, resource type, first/third party and the page they came from; blocked images become a 1×1 pixel so layouts don't break.
- 📚 **Filter list manager** — EasyList, EasyPrivacy, uBlock filters (+ unbreak, quick fixes, privacy, badware), Brave lists, Bypass Paywalls Clean, AdGuard, Fanboy and more, each with its own switch, rule count and last-updated date. Add your own list by link; lists update every 3 days on Wi-Fi (configurable, with "Update now").
- 🧹 **Element hiding, scriptlets & redirects** — lists can now hide leftover ad boxes (`##.ad`, `:has-text`, `:upward`, `#$#` styles), run page fixes (`##+js(...)`: anti-adblock, paywall and popup defusers, `set-constant`, `json-prune`, …) and answer blocked scripts with harmless stand-ins (`$redirect=`), so pages like Google Analytics-dependent sites keep working. Switches under Settings → Ad blocker.
- ✍️ **My rules** — write your own block / allow / hide rules in Settings → Ad blocker → My rules (same syntax as the lists; a bare domain also works). They are applied after every list and win over them, even over `$important` and `@@` exceptions. Lines the engine can't understand are flagged.
- 🎯 **Hide element** — long-press a link or image, choose "Hide element", then tap / use Parent & Child to adjust, Preview, and Hide. It hides instantly, saves a `site##selector` rule to My rules and offers Undo.
- 🔄 Existing hosts lists (Peter Lowe's, StevenBlack) keep working as list entries.

### 📥 Downloads
- 🎛️ **Sort & view sheet** — the sort button is now a Tune icon that opens a bottom sheet: sort by Date / Name / Size / Type (each with a direction toggle), group by None / Date / Type (Today, Yesterday, Last 7 days… or Video, Audio, …), Comfortable or Compact rows, and chips to show / hide the file icon, type badge, size, time taken, date and the Folder / Info buttons. Changes apply live, are remembered, and a Reset button restores the defaults.

## [1.1.0] - 2026-11-14

☁️ **GitHub sync, a smarter download engine, a personal Settings hero, and a lot of polish.**

### ✨ Highlights
- ☁️ **GitHub & Sync** — auto-backup, restorable backup history, and "Send to phone" between your devices.
- 📥 **Downloads you can trust** — real filenames, instant cancel, a safe Clear flow, and no leftover part / yt-dlp / torrent files.
- 💳 **Payment QR codes open your payment app** instead of a web search.
- 👤 **Profile, wallpapers and Status Saver** in a redesigned Settings, with theme-aware quick chips.
- 🎨 **New About header** with an animated light-sweep (Aurora keeps its fixed blue/violet; other themes use their own colours).
- 🎬 **Smarter video detection** — verified streams only, real names and quality, a redesigned "videos found" sheet with a Download button, and per-site opt-out.
- 🌑 **Reworked AMOLED mode** and 🌗 **light-theme heroes**, with a smarter **Force dark** for web pages.
- 🔗 **Resume with a new link** when a download's link has expired.
- 🌐 **Chrome-style favicons**, faster swipe-to-close tabs, and several browser fixes.

<details>
<summary>See all changes since v1.0.0</summary>

### ☁️ GitHub & Sync
- **Dedicated GitHub & Sync screen** under Settings with GitHub sign-in. Data lives in one private Gist with **one file per device**, so two devices never overwrite each other.
- **Auto-backup** — a daily backup plus a ~60 s debounced backup after you change settings (a burst of edits makes a single backup). Scheduling is cancelled when you sign out or switch it off, and only runs when online.
- **Backup history** — a collapsible card with compact rows for the last ~20 Gist revisions, each restorable with one tap.
- **Send to phone** — share any link or magnet from another app to your other device through the share sheet, with an "All devices / specific device" picker. The receiving device checks its inbox when the app opens and every 15 minutes in the background (ETag polling keeps it cheap).
- Network blips retry automatically; rejected sign-ins and rate limits are not retried, so they cannot loop in the background.

### 📥 Downloads
- **Real filename resolution** — while the actual name is still being fetched, the item shows a placeholder and is renamed automatically a few seconds after you start it, instead of keeping a hash-like name.
- **Instant cancel** — the item flips to cancelled immediately instead of waiting for the engine to wind down.
- **Safe Clear** — clearing a *running* download now asks "this will delete and clear", then cancels, deletes the files and clears it. Clear no longer leaves an invisible download running in the background.
- **Full cleanup on Clear / Delete** for cancelled and failed items: direct-download part files and resume sidecars, the yt-dlp cache folder, and torrent content (with a modified-time guard so a still-active file is never removed).
- **Download history cards redesigned** for a cleaner look.
- **Faster filename detection** in the Add Download dialog — the lookups now overlap and are cached, so the real name appears sooner.
- **Names update while downloading** — Start no longer waits for the file-name lookup. If the real name is not known yet, the download begins at once and the item is renamed in the list as soon as the lookup (or the real download response) reveals it; the finished file is saved under the real name, never overwriting an existing file. Tapping Start before the name loads no longer locks in a hash-like name.
- **Resume with a new link** — for a failed *expired* download, the "Select a solution" dialog and the duplicate prompt now offer **Resume existing**: the new link continues from the partial file on disk instead of starting over.
- **Expired-link dialog fixed** — tapping outside or pressing back only closes it and leaves the error as it was; the item is cleared only with the Clear button.

### 🎬 Video detection
**Fewer false results**
- **Verified before listed** — every detected video / stream is probed (HTTP status, Content-Type, size) with the page's own User-Agent, cookies and Referer. Ads, 1×1 stubs, HTML/JSON/image responses and error pages (the "0.0 MB" rows) no longer appear.
- **Manifests are checked for real** — an HLS link must start with `#EXTM3U` and a DASH link must contain `<MPD`.
- **Smaller dedupe** — the same media with a different signed token or cache-buster in the query is one entry, not many.
- **Segments are ignored** — `.ts` / `.m4s` / chunk URLs and `video/mp2t` responses never show up as videos.
- **Fewer misses** — extensionless or signed CDN links are caught from their response Content-Type, and the page's own player (`<video>` / `<audio>`, `fetch` / XHR manifests) reports what it plays.

**Names and quality**
- **Real names** — links with no file name (such as `/videoplayback`) are named from the page title plus the correct extension from the Content-Type (`video/mp4` → `.mp4`, audio → `.m4a`, and so on). Illegal file-name characters are removed and the name is capped at 80 characters. The Add Download dialog keeps this name; only a server-given name that has an extension may replace it. Normal downloads are unaffected.
- **Quality for direct videos** — taken from the page's own player (`videoHeight`), then the CDN's quality code (googlevideo `itag`), then the MP4 header (a small ranged read of the file's first 256 KB). If none of these is available, no quality chip is shown.
- **HLS** rows show the **duration** (for playlists that list every segment) and the available resolutions; DASH rows show resolutions.
- The format label now comes from the Content-Type, so the stray "00" label is gone.

**The "videos found" sheet**
- **Redesigned** — each video is a card with a round icon, a two-line name, and chips for format, quality, duration and size. A clear **Download** button sits beside Play and Copy link. The page-title subtitle was removed because the name already uses it.
- **Download button** on every row: streams open the quality picker; direct files open Add Download with the page as Referer and the browser's cookies.
- **Live** — new and removed videos update while the sheet is open, and the floating button shows a spinner while more links are still being checked.
- **No duplicates** — a file the page downloads itself disappears from the list.
- **Don't detect videos on this site** at the bottom of the sheet; the list of those sites is managed (and reset) from Settings → Browser.

**Downloads from the browser**
- They reuse the tab's real User-Agent (plus cookies and Referer), so protected CDNs that reject a generic UA now serve the file.
- A **Resume available** chip appears when a video matches a failed, expired download in the queue.

### 💳 QR scanner
- Scanning a **UPI or wallet code** (`upi://`, `tez`, `phonepe`, `paytm`, `gpay`, `bhim`, and similar) opens the **payment app chooser** instead of searching the web. If no payment app is installed you get a toast instead of a silent failure.
- The scanner itself is the app's own offline one (CameraX + bundled ML Kit): QR-only decoding, 1080p frames, tap-to-focus, auto-zoom for small codes, torch, and scanning from a gallery image.

### 👤 Settings, Profile & Status Saver
- **Settings hero** with your profile photo and name; the **Status Saver** and **Storage** quick chips sit along its bottom edge. Chips follow the active theme (Aurora keeps its navy look).
- **Profile page** with a circular photo cropper (pinch to zoom, drag, rotate, reset) and name editing.
- **Wallpaper editor** — code-drawn presets or your own image, Fit/Fill, zoom/position/blur/transparency, applied only when you press Save.
- **Status Saver** for WhatsApp and WhatsApp Business statuses, reached from the Status icon in the top bar.
- **Storage screen** showing where XMD's space goes, with per-category colours that stay distinct on every theme.
- **App icon picker** to switch the launcher icon (background + logo combinations).
- More colour themes, including **Aurora**, Catppuccin, Cloudflare, Nord, Rosé Pine, Dracula and others.

### 🎨 About screen
- **Animated header background** — a glowing light-sweep with faint parallel lines and twinkling dots on a deep dark base, tinted from the theme. Aurora uses a fixed blue/violet; every other theme uses its own primary and tertiary colours.
- The **app logo** keeps the launcher icon's background with a soft glow behind it.
- Version chip and the Libraries / GitHub buttons restyled to match the new header.

### 🌑 Appearance
- **AMOLED mode reworked** — pure black backgrounds with softly tinted cards (not grey and dull), brighter text and icon contrast, visible separator lines, and black on every screen, dialog and bottom sheet. System bar scrims are black too, and the edge-to-edge cache now accounts for AMOLED so switching modes updates them immediately.
- **Light themes** get a light, tinted Settings hero and About header; dark themes keep the dark look. The Status Saver and Storage chips adapt to the theme in both modes (white badges with a deeper icon colour in light themes).
- **Aurora keeps its fixed blue / violet** header colours; every other theme tints from its own primary and tertiary colours.

### 🌐 Browser
- **Force dark is smarter** — it prefers a site's own dark theme, darkens only pages that have none, and uses a dark WebView background so there are fewer white flashes while pages load. Pages that paint their own white blocks (ads, iframes, images) can still show them.
- **Favicons rebuilt**: updated after a redirect, sharper quality, shown exactly as the site provides them (rounded or square, like Chrome, no preset clipping), correct sizing in the address bar and suggestions, and a neutral plate behind dark transparent icons such as GitHub.
- **Swipe-to-close on tabs is faster** — 140 ms animation, closes as soon as the row is mostly off-screen, 25 % drag threshold, and fling support.
- **Speed Dial overlap fixed** — opening Speed Dial now hides every WebView, so a stale page from another tab can no longer show through.
- **Background playback fixed for embedded players** — audio/video inside iframes (blog and embedded video players) is now detected, so the playback service starts and keeps playing after you go Home or swipe the app into Recents. The page-visibility override now loads at document start in every frame.
- **Better password-manager autofill** — login fields get proper `autocomplete` hints so Bitwarden, Proton Pass and other autofill services recognise them; single-page logins now finish the autofill session so the "save password?" prompt can appear. Not active in private tabs.
- **Passkey requests handled** — when a site asks for a passkey, which Android only allows system browsers to complete, Xmd answers so the site falls back to your password, and offers to open the page in Chrome (or another browser) where your passkey provider works.
- **Passkey crash fixed** — using passkeys / WebAuthn in the in-app browser no longer crashes (the privileged origin permission is no longer requested).
- The **Status Saver icon** in the top bar is now a full circle instead of a speech bubble with a cut corner.

</details>

---

## [1.0.0] - 2026-09-22

🎉 **First stable release** — out of beta.

### ✨ Highlights
- 🎵 SponsorBlock integration and playlist downloads for yt-dlp links.
- 🚀 3-step launch onboarding, plus a real-time battery-optimization warning banner in Settings.
- 🔁 Resume support for downloads that previously couldn't resume.
- 🍫 Quality picker reworked into a chip grid; format summary + streams-count chip in the Add Download dialog header.
- 💳 New Support section in About (UPI donation card) and live GitHub contributors.
- 🔗 xmd now shows up in Android's "Open with" chooser for direct file links again.

<details>
<summary>See all changes since v1.0.0-beta.6</summary>

### ✨ Added
- 🎵 **SponsorBlock integration and playlist downloads** for yt-dlp links.
- 📝 **Embed-subtitles option** in the Add Download dialog's Advanced section — Off/Embed toggle with language chips, mirroring the SponsorBlock UI.
- 🚀 **3-step launch onboarding flow**, plus a **real-time battery-optimization warning banner** surfaced directly in Settings (replaces the earlier static battery card in Downloads settings).
- 🔁 **Resume support for non-resumable downloads** — downloads that previously couldn't resume can now continue instead of restarting from scratch.
- 💳 **Support section** added to About — UPI donation card with a tap-to-copy ID and a "Send love" button that opens a `upi://pay` intent.
- 👥 About's **Developers section now pulls live GitHub contributors** (avatar, login, commit count, top 8 + View all, loading/error/retry states) instead of a hardcoded list, cached on disk for 72h.
- 🎚️ **Format summary shown in the Add Download dialog header**, plus a streams-count chip.
- 🍫 **Quality picker reworked into a 4-per-row chip grid** (replacing the dropdown menu), with the Audio-format sub-picker (MP3/M4A/Opus/Original) shown as its own chip row when "Audio only" is selected.
- 📥 **Browser downloads now route straight into the full Add Download dialog** — pre-filled with link, detected filename, and page URL — instead of a separate "Add to Downloads?" confirm prompt; this applies to WebView-intercepted downloads too, and expired-link recovery still works through the new path.
- 📆 **Schedule row restyled** (clock icon, label, chevron, dropdown) to match the Save-to card in both the Add Download and Add Torrent dialogs, with the day picker rebuilt as tappable circles so all 7 days fit without clipping.
- 🔗 **xmd now shows up in Android's "Open with" chooser for direct file links** (`.apk`/`.zip`/`.rar`/`.7z`/`.exe`/`.msi`/`.iso`/`.pdf`, e.g. GitHub raw downloads) — added explicit `pathPattern`-based `VIEW` filters to `ShareReceiverActivity`, the same technique used elsewhere in the Morphe ecosystem, since a bare `*/*` mimeType wildcard was losing to apps that declare pathPattern matches.

### 🛠 Fixed
- 🎬 Thumbnail-embedding errors on WebM audio streams from yt-dlp.
- ✂️ Quality chip text no longer gets cropped; resolution labels simplified.
- 🎞️ Duplicate VP9 codec chip in the quality picker (vp9/vp09 now canonicalized to one preset); address bar gained a clear (✕) button while focused and non-empty.
- 🧲 Torrent-file picker no longer shows in the Add Download dialog when it was opened from a browser download click.
- 📤 Reverted an interim `content://`/`file://` local-file-import fix for the "Open with" issue above — it fired on link tap, before any local file existed to import, so it addressed the wrong stage of the flow.
- ⚙️ Assorted compile fixes: restored missing imports and resolved a ViewModel setter signature clash introduced during the downloads refactor below.

### 🗑️ Removed
- 🎚️ The dedicated FPS/Codec dropdown and its header summary were pulled from the quality picker in favor of the plain quality-option selector (later replaced by the chip-grid rework above); the Advanced section's expand icon shrank to a single rotating chevron.

### 🎨 Changed
- 🧵 **Downloads pipeline hardened**: duplicate-aware enqueue and typed queue-state transitions centralized, queue persistence serialized, yt-dlp stop handling made race-safe, navigation/search state preserved across recreation, and release size reduced.
- ⚙️ yt-dlp **download execution, format discovery, and file handling realigned** for consistency across entry points.
- 🔗 About's GitHub link renamed to just "GitHub"; the Home tab is now hidden by default.
- 📐 Download container icon size reduced.

</details>

---

## [1.0.0-beta.6] - 2026-09-12

### ✨ Added
- 🎨 **Full UI migration to Jetpack Compose**, plus a **Material 3 Expressive theming overhaul** — filled icons, an expressive pill-shaped bottom nav, refined theme transitions, dialogs, and download cards across the app.
- 📸 **Instagram downloads** — reels, posts, and stories are now recognized and routed through the yt-dlp quality-picker flow, same as YouTube.
- 📤 **Floating background downloads for shared/external links** — links shared from other apps or opened externally now show a floating dialog and download in the background instead of requiring the main UI.
- 🗂️ **Duplicate-download detection** — path-based detection catches re-downloading a file already in the queue/library, with a resolution strategy (overwrite/rename/skip) instead of silently duplicating.
- ✅ **Multi-selection for downloads** — select multiple queue items for contextual bulk actions, with browser page gestures now isolated so they don't interfere.
- 🗂️ **Grid tabs for the browser** — live tab previews in a grid layout, a list-view switcher, and a Clear All action.
- 🔎 **Search engine selection** — pick your browser's default search engine or set a custom search URL.
- ▶️ **Background playback setting** for browser-played media, so audio/video keeps playing when you leave the browser.
- 🛡️ **Adblock overhaul** — remote-updated hosts list, path/pattern-based blocking, and cosmetic element hiding, on top of faster DNS-over-HTTPS resolution (disk cache, quicker timeouts, main-frame requests no longer proxied).
- 🔍 **Find in page, desktop-site toggle, and improved address bar** — reliable text selection, pinch-to-zoom, tap-to-show suggestions with current-page/clipboard rows, and duplicate-suggestion fixes.
- 🖱️ Chrome-style **swipe page transitions** between bottom navigation tabs.
- 🏠 **Home screen shortcuts** — pin any download link to the device home screen for one-tap re-download, with an "Add to home screen" flow and a toggle to remove shortcuts again.
- 🌐 **Custom browser home page** — Speed Dial, Google, DuckDuckGo, Brave, Bing, Yahoo, or a fully custom URL, instead of always opening Speed Dial.
- 🛡️ **Brave-style Shields** for the in-app browser — Standard/Aggressive/Off blocking levels, a per-site allowlist, and a lifetime blocked-count counter; site whitelisting now lives directly under Shields settings.
- 🗺️ **Google Translate in the browser overflow menu** — opens the current page through Google's web-proxy translator with a language picker.
- 📊 **Daily data limit** — cap total or mobile-only data use for downloads, configurable from Settings.
- 🔁 **Better expired/broken-link recovery** — a redesigned Link Expired dialog offers Retry, Fetch from Browser, or Cancel; retrying now tries a normal re-fetch first and only falls back to opening the browser (and finally a single-connection download) if that fails.
- 🎬 **In-page media picker on YouTube** — a floating "videos found" action appears on YouTube watch/Shorts pages, routing sniffed streams straight into the quality picker.
- 🔄 **Stable/Preview update channel selector** on the About screen, plus a clearer reason shown when an update check fails.
- 📚 **Libraries screen** added to About, crediting open-source dependencies, alongside a 72-hour on-disk avatar cache for the Developers section.
- 📂 **Open / Open Folder** actions on completed download items, and an **Open file location** entry in the downloads long-press sheet.
- ⏸️ **Pause/Resume All** for downloads, with Cancel/Retry/Clear All moved into a top-bar overflow menu, and empty filter chips now hide instead of showing with a zero count.
- 📋 Downloads gained a **copy-error option and a details dialog** for failed items.
- 🗒️ **Clipboard-based auto-start** for direct links, a **Start** chip button, and a magnet-glyph torrent icon.
- 🔍 Website source packs are now scanned from the **whole device**, not a single folder, with a redesigned picker card.
- ✅ **Link validation** in the Add Download dialog before a download starts, catching bad/unsupported links earlier.
- 📘 **Facebook link support** added to the downloader.
- ⏱️ **Completion stats on finished downloads** — duration (e.g. "Took 42s"), size, and localized date/time now shown on completed items, with more reliable filename probing for links.
- 🔎 **In-header settings button and search bar** integrated directly into the app header.
- 🎚️ **Video resolution and audio format dropdowns** added to the download dialog.
- 🌑 **AMOLED dark mode toggle** in theme settings.
- 🎨 **New launcher icon set**, including a proper monochrome silhouette for Android 13+ themed (Monet) icons.

### 🛠 Fixed
- 📤 Sharing a link from YouTube into Xmd no longer keeps the wrong/stale name on the queued item.
- 🧩 Settings has been reorganised for clearer grouping now that Data Limit, Libraries, and the update-channel picker all live there too.
- 🩹 An invalid `--` sequence in an XML comment that could break the manifest merge on release builds, plus other XML-comment syntax errors that were breaking CI.
- ⏳ A stuck yt-dlp queue placeholder that could persist after a download finished, and standalone leftover thumbnail files.
- 📝 About-screen layout and manifest fixes.
- 🧭 The Downloads tab now shows "Downloads" as its header title instead of a generic label.
- 🏗️ Crash opening Appearance settings (vector drawables now used instead of selectors for tab icons), and a Home-tab build failure from a non-lifecycle-aware `StateFlow` collector.
- 🧭 System bars now sync correctly with theme transitions instead of flashing the wrong color, and the floating nav bar shadow no longer clips.
- 📶 Network-gone strings and behavior corrected on the Home tab.

### 🎨 Changed
- 🌐 Site whitelisting for the browser moved from a standalone list into the new Shields settings section, with direct Add from there.
- 🧵 Internal refactor: duplicated logic across UI components, dialogs, and formatters consolidated to cut repeated code, and the project folder structure was reorganized.
- 📦 R8 code minification and resource shrinking re-enabled for release builds.
- 🐛 Debug builds now integrate LeakCanary for memory-leak detection during development.

---

## [1.0.0-beta.5] - 2026-08-29



### ✨ Added
- 🔖 **Real Bookmarks feature** — the star button now saves/removes a proper bookmark (its own DB table) instead of a shortcut, with a new Bookmarks screen (search, swipe-to-delete, Clear all) off the browser overflow menu; the old speed-dial "Bookmark" tiles are renamed to **Shortcut** throughout, with an "Also add as Shortcut" option when bookmarking.
- ⚙️ **Settings redesigned** into a full-screen categorized layout, plus a new **About** screen with a developers/credits section.
- 🗂️ **Tabs tray redesign** — the Tabs dialog is now a bottom sheet instead of a modal `AlertDialog`: pill-shaped rows with round icon avatars and a floating "+" action for opening a new tab.
- 🧲 **Shortcuts**: custom icon override, drag-to-reorder (saved on Done), and fixes for icon clipping and an edit-order bug.
- 🚫 **Domain-blocklist ad blocking** for the in-app browser — new Settings > Browser category, on by default.
- 📤 **Export Websites** (SAF save + share), alongside the existing import; both now live under Browser settings instead of Downloads.
- 🔍 **Search in the Downloads tab**, filtering the queue by file name or source URL, with its own "No matching downloads" empty state.
- 🔗 The browser now handles `fb://` and `intent://` links instead of failing with `ERR_UNKNOWN_URL_SCHEME`.
- 📎 Restored **Copy link** on the Add to Downloads dialog, plus a long-press menu (Open with / Rename / Re-download / Copy download link / Share / Delete) on finished/failed downloads.
- 🎬 Direct `.m3u8`/`.mpd` links are now routed through yt-dlp instead of downloaded raw.
- 🎨 Three new themes: **Tokyo Night**, **Gruvbox**, and **Amethyst**.
- ⚡ Faster search suggestions — instant in-memory history matches, debounce cut from 300ms to 150ms.
- 🔗 The "yt-dlp not installed" dialog's Install button now deep-links straight to Settings > YouTube.

### 🛠 Fixed
- 🎥 Video playback on non-YouTube sites — Range requests used for streaming/seeking were being routed through a DoH proxy whenever a Private DNS mode was set, silently breaking playback everywhere except YouTube's own player pipeline; media and Range requests now bypass the proxy.
- 🔐 Google sign-in no longer fails to persist (removed a WebView UA marker Google blocks, cookies now flushed on pause), and `Set-Cookie` headers from DoH-intercepted requests are now forwarded into `CookieManager` instead of being dropped.
- ⬇️ Multi-connection resume no longer restarts from scratch on a share-link retry, and notification updates are now actually throttled as intended instead of firing unthrottled on every progress tick.
- 🎨 Theme changes now apply immediately from Settings instead of requiring a relaunch.
- ⚙️ Settings icons no longer lose their detail glyphs under tint; the YouTube settings row is correctly hidden on the `lite` flavor.
- 💾 YouTube/Downloads settings now persist immediately on change — no more Save buttons to remember to tap.
- 🩹 Assorted invalid XML-comment (`--`) fixes that were breaking release builds.

### 🎨 Changed
- 📥 Website import/export moved from Downloads settings to Browser settings, where it fits better alongside the adblock toggle.

---

## [1.0.0-beta.4] - 2026-08-28

### ✨ Added
- 🖱️ **Chrome-style long-press menu** — long-press a link or image in the Browser for open-in-new-tab, open-image-in-new-tab, download image, copy link address, and share link, anchored right at the touch point.
- 🔍 **History search** — the History tab now has a search box filtering by title/URL.
- 🕓 **History-aware address bar suggestions** — matching visited pages now show above Google's search-phrase suggestions, each with its own icon.
- 🖥️ **Desktop site toggle** — per-tab overflow menu item that swaps the UA + wide viewport and forces a genuine reload (not served from cache under the old UA).
- 🔎 **Find in page** — Chrome-style overlay bar with prev/next navigation and a live match count.
- ⌨️ **Bottom navigation auto-hides** while the keyboard is open (paste-links box, address bar, find-in-page, any dialog input) instead of floating above the keys.
- 🎚️ **Video preset settings** — pick a preferred container, codec, and FPS ladder for the quick quality picks, plus a default **audio format** (MP3, M4A, Opus, or Original) for Audio-only downloads.
- 📊 **Advanced quality section** — a real yt-dlp format probe (`--dump-json`) surfaced as a collapsible list in the quality picker, showing exact FPS/codec/file size per stream alongside the standard quality ladder.
- 🗂️ **Overflow menu regrouped** into page actions, privacy actions, history, and settings sections for easier scanning.

### 🛠 Fixed
- 🩹 Container/FPS/Codec preset fields in Settings no longer wrap mid-word (e.g. "WebM" splitting into "We"/"bM") — smaller text plus single-line/ellipsize.
- 🎵 The Audio-only row in the quality picker now shows your actual saved audio format (MP3/M4A/Opus/Original) instead of always reading "(MP3)"; a saved "Audio only" default quality also survives a later format change instead of silently reverting to "Ask always".
- 🧭 Downloads tab's Cancel All / Clear All buttons were rendering underneath the bottom navigation bar and never visible — fragment content now clears the nav bar's height properly.
- 🔢 30fps entries in the Advanced quality list weren't labeled with their fps, making them indistinguishable from formats with no fps data — all formats now show their real fps.
- 🖼️ Speed-dial favicons switched from a memory-only cache to a 24-hour on-disk cache, so icons no longer redownload on every cold app launch.
- 🕰️ Fixed the history clock icon rendering as a solid dot instead of an outline, and search-suggestion rows showing the wrong (link) icon instead of a search icon.
- 🔔 Download-started snackbar reliability: no longer appears while already sitting on the Downloads tab, and no longer goes missing on Home/Browser from tab-switch timing races.
- 🏗️ Fixed the `lite` flavor build breaking after the yt-dlp format-probe API was added (its stub `YtDlpManager` needed matching method signatures).

### 🗑️ Removed
- 🕵️ Private browsing tabs' toolbar entry point (the dedicated "New Private Tab" button), pulled back before release.

---

## [1.0.0-beta.3] - 2026-08-26

### ✨ Added
- 📤 **Transparent Share-Receiver** for external downloads — sharing a link from another app (e.g. Morphe's "External downloads" hook) into Xmd no longer bounces the caller app off-screen. A new transparent `ShareReceiverActivity` handles the `SEND` intent directly: YouTube links get a quick bottom-sheet quality picker, everything else (direct/torrent links) queues and starts silently, and the caller app stays in the foreground throughout.
- 🕵️ **In-browser media sniffing** — a new `MediaSniffer` classifier detects HLS/DASH/direct media requests as pages load, surfacing a "videos found" chip and picker sheet in the Browser so streamed media can be grabbed without hunting for a direct link.
- 📶 **Download on Wi-Fi Only** setting (off by default) — live downloads (HTTP, torrent, and YouTube) automatically pause when Wi-Fi drops and resume when it's back; the queue shows "Waiting for Wi-Fi" instead of a generic paused state.
- 🎚️ **Default YouTube quality** setting — choose "Ask always" or lock in a fixed quality to skip the picker sheet on every download.
- 🌐 **Expanded Private DNS options** — added Google DNS and Cloudflare (plain + adblock) DoH providers alongside AdGuard; the list now shows each provider's DoH address as a subtitle, with "Off" reordered to the top.
- 👆 **Tap-to-jump stats pill** — tapping the done/failed count on Home now jumps straight to the relevant Downloads tab.
- 🧲 External torrent links found while browsing can now be added directly from the web.

### 🛠 Fixed
- 🎥 Cleartext HTTP pages (older blogs, direct file hosts) no longer fail to load, and autoplay/JS-triggered `<video>`/`<audio>` no longer hangs on a spinner waiting for a manual tap.
- ❌ Queued items can now be properly cancelled/cleared, and incomplete downloads are rejected instead of being incorrectly marked as `DONE`.
- 🎨 Fixed the theme toggle's tap target (previously too large), bumped header title size, and added a Settings shortcut to the browser's overflow menu.
- 📊 Polished the Browser progress bar (elevation/corners) and snackbar (theme-aware background color).
- 🩹 Fixed two more invalid `--` sequences inside XML comments that were breaking the databinding parser and release builds.

---

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
- ☀️ **Light theme** — every color theme now has a light variant; flip it from the new Dark Mode switch in Settings > Appearance, or with a single tap on the app header.

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
