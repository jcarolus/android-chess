# Native Engine Tests

This directory uses GoogleTest + CTest for the C++ chess engine.

## Prerequisites

- CMake 3.16+
- A C++ compiler with C++17 support
- GoogleTest installed locally

## Run tests

From `native/project/jni`:

```sh
make test
```

This will:

1. Configure CMake into `build-tests/`
2. Build `chess_engine_tests`
3. Run tests with `ctest --output-on-failure`

Run a single test (e.g. 17):

`ctest --test-dir build-tests -I 17,17 --output-on-failure`

## NDK

- Android `ndkBuild` integration (`Android.mk`)

## JNI export regression check

After building an APK, check that every Java native method has an unmangled
export in each packaged ABI. This catches missing declarations in the
`extern "C"` block of `chess-jni.hpp`, which ordinary compilation does not catch.
Run from the repository root, using the installed NDK's `llvm-nm`:

```sh
python3 scripts/check-jni-exports.py path/to/app.apk --nm path/to/ndk/toolchains/llvm/prebuilt/host/bin/llvm-nm
```
