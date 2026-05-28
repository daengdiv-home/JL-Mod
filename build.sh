#!/usr/bin/env bash
set -euo pipefail

ROOT=/workspaces/JL-Mod
OUTPUT="$ROOT/output"

cd "$ROOT"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
ANDROID_HOME=/opt/android-sdk \
  ./gradlew assembleDebug --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m"

mkdir -p "$OUTPUT"
find "$ROOT/app/build/outputs/apk" -name "*.apk" \
  | while read -r apk; do
      rel="${apk#$ROOT/app/build/outputs/apk/}"   # e.g. midlet/debug/JL-Mod_2.0-arm64-v8a.apk
      dest="$OUTPUT/$rel"
      mkdir -p "$(dirname "$dest")"
      cp "$apk" "$dest"
      echo "Copied: $dest"
    done