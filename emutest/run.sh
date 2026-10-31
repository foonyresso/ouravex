#!/usr/bin/env bash
# Runs inside the Android emulator: tries to install every APK, launches it,
# takes screenshots and collects errors into emutest/out/report.txt
set -u
OUT=emutest/out
mkdir -p "$OUT"
R="$OUT/report.txt"
: > "$R"

{
  echo "== device"
  echo "android: $(adb shell getprop ro.build.version.release | tr -d '\r') sdk $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "size: $(adb shell wm size | tr -d '\r')"
} >> "$R"

SZ=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
A=${SZ%x*}; B=${SZ#*x}
if [ "$A" -gt "$B" ]; then W=$A; H=$B; else W=$B; H=$A; fi
echo "landscape W=$W H=$H" >> "$R"

for apk in apks/*.apk; do
  n=$(basename "$apk" .apk)
  pkg=com.foonyresso.ouravex
  [ "$n" = captest ] && pkg=com.foonyresso.captest
  {
    echo ""
    echo "=================== $n ($(stat -c%s "$apk") bytes, pkg $pkg)"
  } >> "$R"
  adb uninstall com.foonyresso.ouravex >/dev/null 2>&1
  adb uninstall com.foonyresso.captest >/dev/null 2>&1
  adb logcat -c
  echo "--- adb install:" >> "$R"
  adb install "$apk" >> "$R" 2>&1
  if adb shell pm list packages | grep -q "$pkg"; then
    echo "RESULT: INSTALLED" >> "$R"
    adb shell dumpsys package "$pkg" | grep -E "versionName|versionCode|targetSdk|minSdk|signatures|flags=" | head -8 >> "$R"
    adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 7
    adb exec-out screencap -p > "$OUT/$n-1-title.png"
    if [ "$n" != captest ]; then
      adb shell input tap $((W / 2)) $((H * 83 / 100))
      sleep 3
      adb exec-out screencap -p > "$OUT/$n-2-intro.png"
      sleep 6
      adb exec-out screencap -p > "$OUT/$n-2-play.png"
      # hold attack button, then move
      AX=$((W - H * 22 / 100)); AY=$((H - H * 21 / 100))
      adb shell input swipe "$AX" "$AY" "$AX" "$AY" 2500 &
      adb shell input swipe $((H * 21 / 100)) $((H * 83 / 100)) $((H * 21 / 100)) $((H * 83 / 100)) 2500 &
      sleep 3
      adb exec-out screencap -p > "$OUT/$n-3-fight.png"
      wait
    fi
    echo "--- crashes:" >> "$R"
    adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|Process: $pkg" | head -30 >> "$R"
    echo "--- still running: $(adb shell pidof "$pkg" | tr -d '\r')" >> "$R"
    adb shell am force-stop "$pkg"
  else
    echo "RESULT: NOT INSTALLED" >> "$R"
    echo "--- logcat (package manager):" >> "$R"
    adb logcat -d | grep -iE "PackageManager|PackageInstaller|installd|Verif|INSTALL_" | tail -40 >> "$R"
  fi
done

cat "$R"
