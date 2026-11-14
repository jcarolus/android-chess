package jwtc.android.chess.engine;

/** Search-only JNI adapter with separate handles for session state and search cancellation. */
public final class RustNative {
    static {
        System.loadLibrary("rust-chess-jni");
    }

    static native long create();

    static native long createSession();

    static native void destroySession(long session);

    static native void stop(long handle);

    static native void destroy(long handle);

    interface ProgressListener {
        void onProgress(RustSearchResult result);
    }

    private static native RustSearchResult search(
            long session,
            long handle,
            long id,
            int variant,
            String fen,
            String[] history,
            int depth,
            int millis,
            boolean quiescence,
            ProgressListener listener);

    static RustSearchResult search(long handle, RustSearchRequest request) {
        return search(handle, request, null);
    }

    static RustSearchResult search(
            long handle, RustSearchRequest request, ProgressListener listener) {
        return search(0, handle, request, listener);
    }

    static RustSearchResult search(
            long session, long handle, RustSearchRequest request, ProgressListener listener) {
        return search(
                session,
                handle,
                request.searchId,
                request.variant.ordinal(),
                request.startingPosition,
                request.getHistory(),
                request.depth,
                request.timeMillis,
                request.quiescence,
                listener);
    }

    /** Blocking search for import jobs; the caller owns the worker thread. */
    public static RustSearchResult search(RustSearchRequest request) {
        long handle = create();
        try {
            return search(handle, request);
        } finally {
            destroy(handle);
        }
    }

    private RustNative() {}
}
