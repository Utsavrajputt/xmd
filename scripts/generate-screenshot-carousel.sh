#!/usr/bin/env bash
# Builds docs/screenshot-carousel.gif -- an auto-scrolling strip of every
# screenshot in fastlane/metadata/android/en-US/images/phoneScreenshots/,
# for embedding in README.md. GitHub's markdown sanitizer strips CSS
# animations and <marquee>, so a single looping GIF is the only reliable
# way to get an "auto-scrolling carousel" effect in a static README --
# this script generates that GIF from whatever screenshots are dropped in
# the phoneScreenshots folder.
#
# Usage: scripts/generate-screenshot-carousel.sh
# Requires: ImageMagick (convert, identify), ffmpeg -- both preinstalled
# on GitHub's ubuntu-latest runners.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCREENSHOTS_DIR="$REPO_ROOT/fastlane/metadata/android/en-US/images/phoneScreenshots"
OUT_DIR="$REPO_ROOT/docs"
OUT_GIF="$OUT_DIR/screenshot-carousel.gif"

# Tune these if the generated GIF comes out too big/slow -- lower
# FRAME_HEIGHT/VIEW_WIDTH/FPS or a shorter LOOP_SECONDS all shrink file
# size. GIF is used here because it's the one format guaranteed to
# actually animate in GitHub's README renderer everywhere it's viewed;
# swapping the final ffmpeg call's `"$OUT_GIF"` for `-c:v libwebp
# -lossless 0 -quality 65 -an -vsync 0 screenshot-carousel.webp` cuts file
# size by ~95% if that ever matters more than guaranteed animation.
FRAME_HEIGHT=420   # each screenshot resized to this height, aspect kept
GAP=28             # px gap between screenshots in the strip
VIEW_WIDTH=860     # visible window width of the final GIF
SPEED=80           # scroll speed in px/sec
FPS=10
LOOP_SECONDS=12    # >= time to scroll past the full strip once

mkdir -p "$OUT_DIR"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

shopt -s nullglob
files=("$SCREENSHOTS_DIR"/*.png "$SCREENSHOTS_DIR"/*.PNG "$SCREENSHOTS_DIR"/*.jpg "$SCREENSHOTS_DIR"/*.JPG "$SCREENSHOTS_DIR"/*.jpeg)
if [ ${#files[@]} -eq 0 ]; then
  echo "No screenshots found in $SCREENSHOTS_DIR -- add some (see its README.md) and re-run." >&2
  exit 1
fi
# Sort by filename so the 0N- numeric prefixes control carousel order.
IFS=$'\n' files=($(printf '%s\n' "${files[@]}" | sort))
unset IFS

echo "Building carousel from ${#files[@]} screenshot(s):"
printf '  %s\n' "${files[@]##*/}"

resized=()
for f in "${files[@]}"; do
  out="$tmp/$(basename "$f").png"
  convert "$f" -resize "x${FRAME_HEIGHT}" -background none -flatten "$out"
  resized+=("$out")
done

# Join left-to-right with a gap after each frame, then duplicate the whole
# strip once so the crop window can wrap seamlessly (frame at x=stripWidth
# is identical to frame at x=0).
convert "${resized[@]}" -background none -splice "${GAP}x0+0+0" +append -chop "${GAP}x0" "$tmp/strip.png"
strip_w="$(identify -format '%w' "$tmp/strip.png")"
convert "$tmp/strip.png" "$tmp/strip.png" +append "$tmp/strip_double.png"

# Render the scrolling crop as a PNG frame sequence first, then hand it to
# ImageMagick for the actual GIF encode. Doing the palette optimization
# with a single ffmpeg filter graph (palettegen+paletteuse) hung
# indefinitely on the environment this was written on -- frame sequence +
# `convert -layers Optimize` is slower but reliable, and produces a much
# smaller file than ffmpeg's default per-frame GIF encoding (a shared
# palette + inter-frame diffing instead of a full independent frame each
# tick).
mkdir -p "$tmp/frames"
frames=$(( LOOP_SECONDS * FPS ))
ffmpeg -y -loop 1 -r "$FPS" -i "$tmp/strip_double.png" -frames:v "$frames" \
  -vf "crop=w=${VIEW_WIDTH}:h=${FRAME_HEIGHT}:x='mod(t*${SPEED}\,${strip_w})':y=0" \
  "$tmp/frames/frame_%04d.png"

convert -delay "$(( 100 / FPS ))" -loop 0 "$tmp"/frames/frame_*.png -layers Optimize "$OUT_GIF"

echo "Wrote $OUT_GIF ($(du -h "$OUT_GIF" | cut -f1))"
