#!/bin/bash
# Uruchamia macierz testów kolejno na emulatorach z różnymi wersjami Androida.
# Użycie: testy/wszystkie_androidy.sh "36 35 33 31"
set -u
export ANDROID_HOME=/home/debian/Android/Sdk ANDROID_SDK_ROOT=/home/debian/Android/Sdk
export TMPDIR=/home/debian/tmp HOME=/home/debian
ADB=$ANDROID_HOME/platform-tools/adb
REPO=$(cd "$(dirname "$0")/.." && pwd)
PORT=5560
SERIAL=emulator-$PORT

for API in ${1:-36 35 33 31}; do
  echo "=== Android API $API ==="
  $ANDROID_HOME/emulator/emulator -avd krawedzie$API -port $PORT -no-window -no-audio -no-boot-anim \
    -no-snapshot -gpu swiftshader_indirect -no-metrics > /home/debian/tmp/emu-$API.log 2>&1 &
  EMU=$!
  $ADB -s $SERIAL wait-for-device
  for i in $(seq 1 180); do
    [ "$($ADB -s $SERIAL shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 2
  done
  sleep 20   # dialer, launcher i usługi Google muszą się dogrzać
  $ADB -s $SERIAL install -r "$REPO/app/build/outputs/apk/debug/app-debug.apk" | tail -1
  $ADB -s $SERIAL install -r "$REPO/tester/build/outputs/apk/debug/tester-debug.apk" | tail -1
  $ADB -s $SERIAL shell input keyevent KEYCODE_WAKEUP
  $ADB -s $SERIAL shell wm dismiss-keyguard
  # Pierwsze uruchomienie aplikacji — tworzy ustawienia.
  $ADB -s $SERIAL shell am start -n pl.oswietlenie.krawedzi/.MainActivity >/dev/null; sleep 4
  $ADB -s $SERIAL shell input keyevent KEYCODE_HOME
  python3 "$REPO/testy/macierz.py" $SERIAL /home/debian/tmp/krawedzie-testy/a$API
  $ADB -s $SERIAL emu kill
  wait $EMU 2>/dev/null
  sleep 5
done
echo "=== KONIEC ==="
