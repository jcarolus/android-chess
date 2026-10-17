APP_ABI          := all
APP_PLATFORM     := 21

# The engine uses the C++ runtime for synchronization and owned containers.
APP_STL := c++_static
APP_CPPFLAGS += -std=c++17
