BUILD_DIR := build-tests

.PHONY: all configure test clean

all: test

configure:
	cmake -S . -B $(BUILD_DIR)

test: configure
	cmake --build $(BUILD_DIR)
	ctest --test-dir $(BUILD_DIR) --output-on-failure

clean:
	rm -rf $(BUILD_DIR)
