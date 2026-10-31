#!/usr/bin/env bash
# Builds the native Java game inside a Capacitor-generated Android project
# (same toolchain/manifest style as the zvuchar app, which installs fine).
set -euo pipefail
cd "$(dirname "$0")"
SRC=../android-game/app/src/main

npm install --no-audit --no-fund
rm -rf android
npx cap add android
npx cap sync android

APP=android/app/src/main
JAVA_DIR="$APP/java/com/foonyresso/ouravex"
mkdir -p "$JAVA_DIR"
rm -f "$JAVA_DIR"/*.java
cp "$SRC"/java/com/foonyresso/ouravex/*.java "$JAVA_DIR"/

# game strings (without app_name, Capacitor already defines it)
python3 - "$SRC/res/values/strings.xml" "$APP/res/values/game_strings.xml" <<'PY'
import sys,re
src=open(sys.argv[1],encoding='utf-8').read()
src=re.sub(r'\s*<string name="app_name">.*?</string>','',src)
open(sys.argv[2],'w',encoding='utf-8').write(src)
PY

# landscape
sed -i 's/<activity/<activity android:screenOrientation="sensorLandscape"/' "$APP/AndroidManifest.xml"

(cd android && ./gradlew --no-daemon assembleDebug)
mkdir -p out
cp android/app/build/outputs/apk/debug/app-debug.apk out/ouravex-cap.apk
ls -la out
