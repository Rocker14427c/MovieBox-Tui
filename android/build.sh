#!/usr/bin/env bash
# MovieBox Android — one-shot build environment + APK build.
#
# Runs on a machine with internet access. It:
#   1. Ensures a JDK 17+ is present (uses JAVA_HOME if set, else looks for javac).
#   2. Downloads the Android command-line tools and installs platform-35 +
#      build-tools (into ANDROID_HOME, default ~/Android/Sdk).
#   3. Accepts SDK licenses.
#   4. Builds a debug APK with the Gradle wrapper.
#
# Usage:  ./build.sh [--release]
# Output: app/build/outputs/apk/debug/app-debug.apk  (or release/)

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_DIR"

MODE="debug"
if [[ "${1:-}" == "--release" ]]; then
    MODE="release"
fi

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
CMDLINE_TOOLS="$ANDROID_HOME/cmdline-tools/latest"
SDKMANAGER="$CMDLINE_TOOLS/bin/sdkmanager"
PLATFORM="platforms;android-35"
BUILD_TOOLS="build-tools;35.0.0"

log() { printf '\033[1;36m[moviebox]\033[0m %s\n' "$*"; }

# ---------------------------------------------------------------- 1. JDK
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
elif command -v javac >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
    export JAVA_HOME
else
    log "No JDK found. Install a JDK 17+ first, e.g.:"
    log "  Ubuntu/Debian:  sudo apt install -y openjdk-17-jdk"
    log "  macOS (brew):   brew install --openjdk@17"
    log "  or set JAVA_HOME and re-run this script."
    exit 1
fi
log "Using JDK: $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

# ---------------------------------------------------------------- 2. Android SDK
mkdir -p "$ANDROID_HOME"
if [[ ! -x "$SDKMANAGER" ]]; then
    log "Downloading Android command-line tools…"
    TOOLS_ZIP="$(mktemp -d)/cmdtools.zip"
    URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$URL" -o "$TOOLS_ZIP"
    else
        wget -q "$URL" -O "$TOOLS_ZIP"
    fi
    mkdir -p "$CMDLINE_TOOLS"
    unzip -q "$TOOLS_ZIP" -d "$CMDLINE_TOOLS"
    # The zip unpacks to cmdline-tools/bin; move it to the expected layout.
    mkdir -p "$CMDLINE_TOOLS"
    if [[ -d "$CMDLINE_TOOLS/cmdline-tools" ]]; then
        mv "$CMDLINE_TOOLS/cmdline-tools" "$CMDLINE_TOOLS/latest"
    fi
    log "Command-line tools installed."
fi

log "Installing $PLATFORM and $BUILD_TOOLS…"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "$PLATFORM" "$BUILD_TOOLS" "platform-tools" >/dev/null

# ---------------------------------------------------------------- 3. local.properties
echo "sdk.dir=$ANDROID_HOME" > local.properties

# ---------------------------------------------------------------- 4. Build
log "Building $MODE APK with Gradle…"
if [[ "$MODE" == "release" ]]; then
    ./gradlew :app:assembleRelease --no-daemon
else
    ./gradlew :app:assembleDebug --no-daemon
fi

APK="app/build/outputs/apk/$MODE/app-$MODE.apk"
log "Done. APK: $REPO_DIR/$APK"
log "Install on a connected device/emulator with:  adb install -r \"$REPO_DIR/$APK\""
