package jwtc.android.chess.services;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

/**
 * Compact hash->string map file:
 *
 * Header (16 bytes, little-endian):
 *  0  : char[4] magic = "HMAP"
 *  4  : uint16  version = 1
 *  6  : uint16  reserved = 0
 *  8  : uint32  count = N
 * 12  : uint32  crc32 of (entries bytes || blob bytes). 0 means "not set".
 *
 * Entries (N * 16 bytes, little-endian), sorted by hash:
 *  struct Entry {
 *      uint64 hash;
 *      uint32 offset; // byte offset into blob
 *      uint16 len;    // string length in bytes (UTF-8)
 *      uint16 pad;    // 0
 *  }
 *
 * Blob: concatenated UTF-8 bytes of each string, no terminator.
 */
public final class HMap {
    private static final String TAG = "HMap";
    private static final int HEADER_SIZE = 16;
    private static final int ENTRY_SIZE  = 16;

    private final Entry[] entries;  // sorted by hash
    private final byte[] blob;

    private HMap(Entry[] entries, byte[] blob) {
        this.entries = entries;
        this.blob = blob;
    }

    /** Single entry in the on-disk table (kept in-memory as well). */
    public static final class Entry {
        public final long hash;
        public final int offset;
        public final int len;

        public Entry(long hash, int offset, int len) {
            this.hash = hash;
            this.offset = offset;
            this.len = len;
        }
    }

    /* ============================
     *            READ
     * ============================ */

    public static HMap read(Context context, Uri uri) throws IOException {
        ContentResolver cr = context.getContentResolver();
        try (AssetFileDescriptor afd = cr.openAssetFileDescriptor(uri, "r")) {
            if (afd == null) throw new FileNotFoundException("Unable to open: " + uri);

            long length = afd.getLength(); // may be UNKNOWN_LENGTH (-1) for some providers
            long start  = afd.getStartOffset();

            try (FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
                 FileChannel ch = fis.getChannel()) {

                if (length >= 0) {
                    // We know the content length; use bounded reads.
                    return readFromBoundedChannel(ch, start, start + length);
                } else {
                    // Fallback when provider doesn't report length: buffer to memory once.
                    // (Large files: if this worries you, stream into a temp file in cache and map from there.)
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    // Position the channel to start
                    ch.position(start);
                    ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
                    while (true) {
                        buf.clear();
                        int r = ch.read(buf);
                        if (r <= 0) break;
                        baos.write(buf.array(), 0, r);
                    }
                    byte[] all = baos.toByteArray();
                    return readFromByteArray(all);
                }
            }
        }
    }

    public static HMap read(AssetManager assets, String assetPath) throws IOException {
        try (InputStream is = assets.open(assetPath, AssetManager.ACCESS_BUFFER)) {
            // Read the whole asset (works even if it's compressed in the APK)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            byte[] all = baos.toByteArray();
            return readFromByteArray(all); // uses the helper you already have
        }
    }

    private static HMap readFromByteArray(byte[] all) throws IOException {
        if (all.length < HEADER_SIZE) throw new IOException("File too small for header");
        ByteBuffer hdr = ByteBuffer.wrap(all, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        byte[] magic = new byte[4];
        hdr.get(magic);
        if (!(magic[0]=='H' && magic[1]=='M' && magic[2]=='A' && magic[3]=='P')) {
            throw new IOException("Bad magic");
        }
        int version = Short.toUnsignedInt(hdr.getShort());
        if (version != 1) throw new IOException("Unsupported version: " + version);
        hdr.getShort(); // reserved
        int count = hdr.getInt();
        long hdrCrc = Integer.toUnsignedLong(hdr.getInt());

        long entriesBytesLen = (long) count * ENTRY_SIZE;
        long blobLen = (all.length - HEADER_SIZE) - entriesBytesLen;
        if (entriesBytesLen < 0 || blobLen < 0) throw new IOException("Truncated content");

        byte[] entriesBytes = Arrays.copyOfRange(all, HEADER_SIZE, (int)(HEADER_SIZE + entriesBytesLen));
        byte[] blob         = Arrays.copyOfRange(all, (int)(HEADER_SIZE + entriesBytesLen), all.length);

        if (hdrCrc != 0) {
            CRC32 crc = new CRC32();
            crc.update(entriesBytes);
            crc.update(blob);
            long calc = Integer.toUnsignedLong((int) crc.getValue());
            if (calc != hdrCrc) throw new IOException("CRC mismatch");
        }

        Entry[] entries = new Entry[count];
        ByteBuffer ebuf = ByteBuffer.wrap(entriesBytes).order(ByteOrder.LITTLE_ENDIAN);
        long prev = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            long h = ebuf.getLong();
            int off = ebuf.getInt();
            int len = Short.toUnsignedInt(ebuf.getShort());
            ebuf.getShort();
            if ((long) off + (long) len > blob.length) throw new IOException("Entry out of bounds");
            if (i > 0 && h <= prev) throw new IOException("Entries not strictly sorted");
            prev = h;
            entries[i] = new Entry(h, off, len);
        }
        return new HMap(entries, blob);
    }

    // Parse from a FileChannel with known [start,end) bounds (AssetFileDescriptor case)
    private static HMap readFromBoundedChannel(FileChannel ch, long start, long end) throws IOException {
        long size = end - start;
        if (size < HEADER_SIZE) throw new IOException("File too small");
        // Read header (16)
        ByteBuffer hdr = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        ch.position(start);
        readFully(ch, hdr);
        hdr.flip();

        byte[] magic = new byte[4];
        hdr.get(magic);
        if (!(magic[0]=='H' && magic[1]=='M' && magic[2]=='A' && magic[3]=='P')) {
            throw new IOException("Bad magic");
        }
        int version = Short.toUnsignedInt(hdr.getShort());
        if (version != 1) throw new IOException("Unsupported version: " + version);
        hdr.getShort(); // reserved
        int count = hdr.getInt();
        long hdrCrc = Integer.toUnsignedLong(hdr.getInt());

        long entriesBytesLen = (long) count * ENTRY_SIZE;
        long remaining = size - HEADER_SIZE;
        if (entriesBytesLen > remaining) throw new IOException("Truncated entries");
        long blobLen = remaining - entriesBytesLen;

        byte[] entriesBytes = new byte[(int) entriesBytesLen];
        if (entriesBytes.length > 0) {
            ByteBuffer eb = ByteBuffer.wrap(entriesBytes).order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, eb);
        }
        byte[] blob = new byte[(int) blobLen];
        if (blob.length > 0) {
            ByteBuffer bb = ByteBuffer.wrap(blob);
            readFully(ch, bb);
        }

        if (hdrCrc != 0) {
            CRC32 crc = new CRC32();
            crc.update(entriesBytes);
            crc.update(blob);
            long calc = Integer.toUnsignedLong((int) crc.getValue());
            if (calc != hdrCrc) throw new IOException("CRC mismatch");
        }

        Entry[] entries = new Entry[count];
        ByteBuffer ebuf = ByteBuffer.wrap(entriesBytes).order(ByteOrder.LITTLE_ENDIAN);
        long prev = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            long h = ebuf.getLong();
            int off = ebuf.getInt();
            int len = Short.toUnsignedInt(ebuf.getShort());
            ebuf.getShort();
            if ((long) off + (long) len > blob.length) throw new IOException("Entry out of bounds");
            if (i > 0 && h <= prev) throw new IOException("Entries not strictly sorted");
            prev = h;
            entries[i] = new Entry(h, off, len);
        }
        return new HMap(entries, blob);
    }

    /** Binary search; returns null if not found. */
    public String get(long hash) {
        int lo = 0, hi = entries.length - 1;
        Log.d(TAG, "entries " + entries.length + " " + hash);
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long mh = entries[mid].hash;
            if      (mh < hash) lo = mid + 1;
            else if (mh > hash) hi = mid - 1;
            else {
                Entry e = entries[mid];
                return new String(blob, e.offset, e.len, StandardCharsets.UTF_8);
            }
        }
        return null;

//        for (Entry entry : entries) {
//            if (entry.hash == hash) {
//               return new String(blob, entry.offset, entry.len, StandardCharsets.UTF_8);
//            }
//        }
//        return null;
    }

    /* ============================
     *           WRITE
     * ============================ */

    /** Convenience pair for writing. */
    public static final class Pair {
        public final long hash;
        public final String value;
        public Pair(long hash, String value) { this.hash = hash; this.value = value; }
    }

    public static void write(Context context, Uri uri, Collection<Pair> pairs) throws IOException {
        // Build the same bytes as the existing path-based writer, but buffer first
        ArrayList<Pair> list = new ArrayList<>(pairs);

        // @TODO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list.sort(Comparator.comparingLong(p -> p.hash));
        }

        // entries + blob
        long prev = Long.MIN_VALUE;
        int n = list.size();
        byte[] entriesBytes = new byte[n * ENTRY_SIZE];
        ByteBuffer ebuf = ByteBuffer.wrap(entriesBytes).order(ByteOrder.LITTLE_ENDIAN);

        ByteArrayOutputStream blobOut = new ByteArrayOutputStream(Math.max(1024, n * 16));
        int offset = 0;
        for (int i = 0; i < n; i++) {
            Pair p = list.get(i);
            if (i > 0 && p.hash == prev) {
                throw new IllegalArgumentException("Duplicate hash: " + Long.toUnsignedString(p.hash));
            }
            prev = p.hash;
            byte[] bytes = p.value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 0xFFFF) {
                throw new IllegalArgumentException("String too long (>65535) for hash " + Long.toUnsignedString(p.hash));
            }
            ebuf.putLong(p.hash);
            ebuf.putInt(offset);
            ebuf.putShort((short) bytes.length);
            ebuf.putShort((short) 0);

            blobOut.write(bytes, 0, bytes.length);
            offset += bytes.length;
        }
        byte[] blob = blobOut.toByteArray();

        // header + CRC32
        CRC32 crc = new CRC32();
        crc.update(entriesBytes);
        crc.update(blob);
        int crc32 = (int) crc.getValue();

        byte[] header = new byte[HEADER_SIZE];
        ByteBuffer hdr = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        hdr.put((byte) 'H').put((byte) 'M').put((byte) 'A').put((byte) 'P');
        hdr.putShort((short) 1);
        hdr.putShort((short) 0);
        hdr.putInt(n);
        hdr.putInt(crc32);

        // Write in one pass to the Uri (truncates existing)
        ContentResolver cr = context.getContentResolver();
        try (OutputStream os = cr.openOutputStream(uri, "w")) {
            if (os == null) throw new FileNotFoundException("Unable to open for write: " + uri);
            os.write(header);
            os.write(entriesBytes);
            os.write(blob);
            os.flush();
            // (No atomic rename available for arbitrary content providers.)
        }
    }

    // ADD: convenience to write from Map<Long,String> on Android 11+
    public static void writeFromMap(Context context, Uri uri, Map<Long, String> map) throws IOException {
        ArrayList<Pair> pairs = new ArrayList<>(map.size());
        for (Map.Entry<Long, String> e : map.entrySet()) {
            pairs.add(new Pair(e.getKey(), e.getValue()));
        }
        write(context, uri, pairs);
    }

    /* ============================
     *         UTILITIES
     * ============================ */

    private static void readFully(FileChannel ch, ByteBuffer dst) throws IOException {
        while (dst.hasRemaining()) {
            int r = ch.read(dst);
            if (r < 0) throw new EOFException("Unexpected EOF");
        }
    }
}
