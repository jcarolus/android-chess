cmake_minimum_required(VERSION 3.16)

include(GoogleTest)

find_package(Threads REQUIRED)
find_package(GTest CONFIG REQUIRED)

add_library(chess_engine STATIC
    ../BoardStack.cpp
    ../SearchWorkspace.cpp
    ../Pos.cpp
    ../ChessBoard.cpp
    ../ChessBoardState.cpp
    ../ChessBoardNotation.cpp
    ../Move.cpp
    ../Game.cpp
    ../GameSearch.cpp
)

target_include_directories(chess_engine PUBLIC ../)
target_compile_definitions(chess_engine PUBLIC TERMINAL)

add_executable(chess_engine_tests
    chess-test.cpp
    engine_tests.cpp
)

target_link_libraries(chess_engine_tests
    PRIVATE
        chess_engine
        GTest::gtest_main
        Threads::Threads
)

gtest_discover_tests(chess_engine_tests)
