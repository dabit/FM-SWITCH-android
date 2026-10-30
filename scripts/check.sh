#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${JAVA_HOME:-}" ]]; then
    export JAVA_HOME
elif command -v mise >/dev/null 2>&1; then
    export JAVA_HOME="$(mise where java@temurin-21)"
else
    echo "Set JAVA_HOME to a JDK 21." >&2
    exit 1
fi

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [[ ! -d "$ANDROID_HOME/platforms" ]]; then
    echo "Android SDK not found at $ANDROID_HOME; set ANDROID_HOME to an SDK with platforms/." >&2
    exit 1
fi

step() {
    echo "==> $1"
    shift
    "$@"
}

run_group() {
    case "$1" in
        lint) step lint ./gradlew spotlessCheck :app:lintDebug ;;
        build) step build ./gradlew :app:assembleDebug :fm1:assemble :model:assemble ;;
        test) step test ./gradlew :fm1:test :model:test :app:testDebugUnitTest ;;
        device)
            if ! adb devices | sed 1d | grep -q 'device$'; then
                echo "No Android device is attached."
                return 1
            fi
            step device ./gradlew :app:connectedDebugAndroidTest
            ;;
        *)
            echo "Unknown check group: $1" >&2
            return 1
            ;;
    esac
}

groups=("$@")
if [[ ${#groups[@]} -eq 0 ]]; then
    groups=(lint build test)
fi

for group in "${groups[@]}"; do
    run_group "$group"
done

echo "Checks passed: ${groups[*]}"
