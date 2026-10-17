#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
# GUI-launched tools (Android Studio) don't inherit the PATH a login shell
# gets from .zshrc/.bash_profile, so rustup's cargo is otherwise invisible.
[ -f "$HOME/.cargo/env" ] && . "$HOME/.cargo/env"
ndk="${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK directory}"
cargo="${CARGO:-cargo}"
case "$(uname -s)" in
  Darwin) host=darwin-x86_64 ;;
  Linux) host=linux-x86_64 ;;
  *) echo 'Build Rust Android libraries from macOS or Linux' >&2; exit 1 ;;
esac
for spec in 'arm64-v8a:aarch64-linux-android:aarch64-linux-android' 'armeabi-v7a:armv7-linux-androideabi:armv7a-linux-androideabi' 'x86:i686-linux-android:i686-linux-android' 'x86_64:x86_64-linux-android:x86_64-linux-android'; do
  IFS=: read -r abi target clang <<< "$spec"
  key="$(echo "$target" | tr '[:lower:]-' '[:upper:]_')"
  env "CARGO_TARGET_${key}_LINKER=$ndk/toolchains/llvm/prebuilt/$host/bin/${clang}24-clang" \
    "$cargo" build --manifest-path "$root/native/rust/Cargo.toml" --locked --release --target "$target" -p rust-chess-bridge
  mkdir -p "$root/native/rust/target/android/$abi"
  cp "$root/native/rust/target/$target/release/librust_chess_bridge.a" "$root/native/rust/target/android/$abi/"
done
