#!/bin/bash
SRC="iMAX logo.png"

echo "Creating mipmap directories..."
mkdir -p app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}

# Calculate dimensions
# Standard/Round icons: standard size, let's keep it similar but with a slight padding if needed. We'll use 80% size for the logo, centered.
# mdpi: 48x48. Logo size: ~38x38
# hdpi: 72x72. Logo size: ~58x58
# xhdpi: 96x96. Logo size: ~76x76
# xxhdpi: 144x144. Logo size: ~116x116
# xxxhdpi: 192x192. Logo size: ~154x154

echo "Generating standard icons..."
magick "$SRC" -resize 38x38 -background transparent -gravity center -extent 48x48 app/src/main/res/mipmap-mdpi/ic_launcher.png
magick "$SRC" -resize 58x58 -background transparent -gravity center -extent 72x72 app/src/main/res/mipmap-hdpi/ic_launcher.png
magick "$SRC" -resize 76x76 -background transparent -gravity center -extent 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher.png
magick "$SRC" -resize 116x116 -background transparent -gravity center -extent 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher.png
magick "$SRC" -resize 154x154 -background transparent -gravity center -extent 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher.png

echo "Generating round icons..."
magick "$SRC" -resize 38x38 -background transparent -gravity center -extent 48x48 app/src/main/res/mipmap-mdpi/ic_launcher_round.png
magick "$SRC" -resize 58x58 -background transparent -gravity center -extent 72x72 app/src/main/res/mipmap-hdpi/ic_launcher_round.png
magick "$SRC" -resize 76x76 -background transparent -gravity center -extent 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher_round.png
magick "$SRC" -resize 116x116 -background transparent -gravity center -extent 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
magick "$SRC" -resize 154x154 -background transparent -gravity center -extent 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png

# Adaptive foregrounds: 108dp canvas, 72dp safe zone. Ratio is 72/108 = 0.666
# mdpi: 108x108, logo: 72x72
# hdpi: 162x162, logo: 108x108
# xhdpi: 216x216, logo: 144x144
# xxhdpi: 324x324, logo: 216x216
# xxxhdpi: 432x432, logo: 288x288

echo "Generating adaptive foreground icons (safe zone padded)..."
magick "$SRC" -resize 72x72 -background transparent -gravity center -extent 108x108 app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png
magick "$SRC" -resize 108x108 -background transparent -gravity center -extent 162x162 app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png
magick "$SRC" -resize 144x144 -background transparent -gravity center -extent 216x216 app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png
magick "$SRC" -resize 216x216 -background transparent -gravity center -extent 324x324 app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png
magick "$SRC" -resize 288x288 -background transparent -gravity center -extent 432x432 app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png

echo "Cleaning up redundant xml/webp resources..."
find app/src/main/res -name "ic_launcher_foreground.xml" -type f -delete
find app/src/main/res -name "ic_launcher*.webp" -type f -delete

echo "Icons generated successfully."
