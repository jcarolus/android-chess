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

## NDK

- Android `ndkBuild` integration (`Android.mk`)
