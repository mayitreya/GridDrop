#!/usr/bin/env bash
# One-command build using the self-contained toolchain installed under $HOME.
# Produces the slim (R8-shrunk, resource-shrunk) release APK, debug-signed so it still sideloads.
set -e
export JAVA_HOME="$HOME/.local/opt/jdk-21"
export ANDROID_HOME="$HOME/Android/Sdk"
cd "$(dirname "$0")"
./gradlew :app:assembleRelease --console=plain "$@"
cp -f app/build/outputs/apk/release/app-release.apk ./griddrop-slim.apk
echo "APK -> $(pwd)/griddrop-slim.apk"
