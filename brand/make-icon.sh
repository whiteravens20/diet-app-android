#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Writes the application's adaptive icon from the mark's two drawings in this folder, with
# ImageMagick: the coloured symbol (symbol.png, on a transparent ground) as the icon's foreground,
# and the flat glyph (glyph.png, green on white) as its monochrome layer, turned to lie as the
# symbol's leaf lies. The background is a gradient drawn by drawable/ic_launcher_background.xml.
# Each layer keeps its shape inside the 66 dp a launcher's mask leaves whole. Run it from the
# repository's root when a drawing changes, and commit what it writes:
#
#   brand/make-icon.sh
set -euo pipefail
res=app/src/main/res
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
# Where each drawing's shape lies on its 1024 px canvas: the symbol's box centre and the farthest
# point of its shape from it, and the glyph's.
SYMBOL_X=528; SYMBOL_Y=513; SYMBOL_REACH=522
GLYPH_REACH=426
# The symbol reaches the edge of the safe zone; the glyph, whose leaf is wider, a little less.
SYMBOL_DP=33; GLYPH_DP=27
convert brand/glyph.png -colorspace gray -negate -evaluate multiply 2 -background black -rotate 42 \
  -gravity center -extent 1024x1024 +repage "$work/glyph-alpha.png"
convert -size 1024x1024 xc:white "$work/glyph-alpha.png" -alpha off -compose copy-opacity -composite "$work/glyph.png"
for density in mdpi:1 hdpi:1.5 xhdpi:2 xxhdpi:3 xxxhdpi:4; do
  name=${density%%:*}; scale=${density##*:}
  size=$(awk "BEGIN { printf \"%d\", 108 * $scale + 0.5 }")
  symbol=$(awk "BEGIN { printf \"%d\", 1024 * $SYMBOL_DP * $scale / $SYMBOL_REACH + 0.5 }")
  x=$(awk "BEGIN { printf \"%d\", $size / 2 - $SYMBOL_X * $symbol / 1024 + 0.5 }")
  y=$(awk "BEGIN { printf \"%d\", $size / 2 - $SYMBOL_Y * $symbol / 1024 + 0.5 }")
  glyph=$(awk "BEGIN { printf \"%d\", 1024 * $GLYPH_DP * $scale / $GLYPH_REACH + 0.5 }")
  at=$(awk "BEGIN { printf \"%d\", ($size - $glyph) / 2 + 0.5 }")
  mkdir -p "$res/mipmap-$name"
  convert -size "${size}x${size}" xc:none \( brand/symbol.png -resize "${symbol}x${symbol}" \) -geometry "+$x+$y" -composite -strip "$res/mipmap-$name/ic_launcher_foreground.png"
  convert -size "${size}x${size}" xc:none \( "$work/glyph.png" -resize "${glyph}x${glyph}" \) -geometry "+$at+$at" -composite -strip "$res/mipmap-$name/ic_launcher_monochrome.png"
done
echo "The icon's layers are written under $res/mipmap-*."
