package jwtc.android.chess.engine;

/** Search-only JNI adapter. Handles are confined to one RustLocalEngine worker. */
final class RustNative {
    static { System.loadLibrary("rust-chess-jni"); }
    static native long create();
    static native void stop(long handle);
    static native void destroy(long handle);
    private static native RustSearchResult search(long handle, long id, int variant, String fen,
                                                  String[] history, int depth, int millis, boolean quiescence);
    static RustSearchResult search(long handle, RustSearchRequest request) {
        return search(handle, request.searchId, request.variant.ordinal(), request.startingPosition,
            request.getHistory(), request.depth, request.timeMillis, request.quiescence);
    }
    private RustNative() {}
}
