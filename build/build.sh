#!/usr/bin/env bash
# Builds the fukbd phone and TV debug APKs from the pinned FUTO fork plus this fukbd-ex checkout
# (fukbd-docs M0, requirement R1; design.md Interface and Refuses).
#
#   build/build.sh [--fork <dir>] [--variant phone|tv|both] [--test]
#
# --fork defaults to ../android-keyboard next to this checkout. --test also runs the core JVM tests.
# Output: out/<variant>.apk and out/<variant>.badging.txt. Exit 2: a pin or a clean-tree check
# failed. Exit 3: no Android SDK.
set -euo pipefail

EX="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FORK="$(dirname "$EX")/android-keyboard"
VARIANT=both
TEST=0
while [ $# -gt 0 ]; do
  case "$1" in
    --fork) FORK="$(cd "$2" && pwd)"; shift 2 ;;
    --variant) VARIANT="$2"; shift 2 ;;
    --test) TEST=1; shift ;;
    *) echo "usage: $0 [--fork <dir>] [--variant phone|tv|both] [--test]" >&2; exit 64 ;;
  esac
done
case "$VARIANT" in phone|tv|both) ;; *) echo "--variant must be phone, tv or both" >&2; exit 64 ;; esac

# shellcheck source=pins.env
source "$EX/build/pins.env"
refuse() { echo "refused: $*" >&2; exit 2; }

[ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ] || { echo "Android SDK not found: set ANDROID_HOME" >&2; exit 3; }
for d in "platforms/$ANDROID_PLATFORM" "build-tools/$BUILD_TOOLS" "ndk/$NDK_VERSION" "cmake/$CMAKE_VERSION"; do
  [ -d "$ANDROID_HOME/$d" ] || refuse "SDK package $d missing in $ANDROID_HOME"
done

java_major="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')"
[ "$java_major" = "$JDK_MAJOR" ] || refuse "JDK $java_major on PATH, pinned $JDK_MAJOR (set JAVA_HOME and PATH)"

[ -d "$FORK/.git" ] || refuse "no fork checkout at $FORK"
fork_head="$(git -C "$FORK" rev-parse HEAD)"
[ "$fork_head" = "$FORK_SHA" ] || refuse "fork at $fork_head, pinned $FORK_SHA"
[ -z "$(git -C "$FORK" status --porcelain)" ] || refuse "fork tree $FORK has uncommitted changes"
if git -C "$FORK" submodule status | grep -E '^[-+U]' >/dev/null; then
  refuse "fork submodules are missing or not at the recorded commits (git -C $FORK submodule update --init --recursive)"
fi
[ -z "$(git -C "$EX" status --porcelain)" ] || refuse "fukbd-ex tree $EX has uncommitted changes"

tasks=()
[ "$TEST" = 1 ] && tasks+=(":fukbd-core:test")
case "$VARIANT" in
  phone) tasks+=(assembleStableFukbdPhoneDebug) ;;
  tv) tasks+=(assembleStableFukbdTvDebug) ;;
  both) tasks+=(assembleStableFukbdPhoneDebug assembleStableFukbdTvDebug) ;;
esac

echo "fork $fork_head (upstream $UPSTREAM_SHA), fukbd-ex $(git -C "$EX" rev-parse HEAD), JDK $java_major"
"$FORK/gradlew" -p "$FORK" -Pfukbd.ex.dir="$EX" "${tasks[@]}"

mkdir -p "$EX/out"
aapt2="$ANDROID_HOME/build-tools/$BUILD_TOOLS/aapt2"
for v in phone tv; do
  [ "$VARIANT" = both ] || [ "$VARIANT" = "$v" ] || continue
  flavor="fukbd$(tr '[:lower:]' '[:upper:]' <<<"${v:0:1}")${v:1}"
  apk="$(find "$FORK/build/outputs/apk/stable${flavor^}/debug" -name '*.apk' | head -n1)"
  [ -n "$apk" ] || { echo "no APK for $v under $FORK/build/outputs/apk" >&2; exit 1; }
  cp "$apk" "$EX/out/$v.apk"
  "$aapt2" dump badging "$EX/out/$v.apk" > "$EX/out/$v.badging.txt"
  echo "$v: $EX/out/$v.apk  $(grep -o "^package: name='[^']*'" "$EX/out/$v.badging.txt")"
done
