#!/usr/bin/env bash
set -euo pipefail
directory="$RUNNER_TEMP/senada-qa"
build_tools="$ANDROID_HOME/build-tools/35.0.0"
android_jar="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p "$directory/classes" "$directory/dex"
javac --release 8 -encoding UTF-8 -cp "$android_jar" -d "$directory/classes" .github/qa/PlaybackProbe.java
find "$directory/classes" -name '*.class' -print0 | xargs -0 "$build_tools/d8" --lib "$android_jar" --min-api 26 --output "$directory/dex"
"$build_tools/aapt2" link -I "$android_jar" --manifest .github/qa/AndroidManifest.xml -o "$directory/unsigned.apk"
zip -j "$directory/unsigned.apk" "$directory/dex/classes.dex"
"$build_tools/zipalign" -f 4 "$directory/unsigned.apk" "$directory/probe.apk"
keytool -genkeypair -keystore "$directory/probe.jks" -storepass android -keypass android -alias probe -keyalg RSA -validity 1 -dname CN=SenadaQA
"$build_tools/apksigner" sign --ks "$directory/probe.jks" --ks-pass pass:android "$directory/probe.apk"
adb install qa-build/composeApp-debug.apk
adb install "$directory/probe.apk"
adb shell am instrument -w io.github.zeeynrs.senada.qa/.PlaybackProbe | tee "$directory/result.txt"
grep -q '^INSTRUMENTATION_CODE: -1' "$directory/result.txt"
