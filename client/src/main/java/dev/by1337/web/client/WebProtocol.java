package dev.by1337.web.client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class WebProtocol {
    public static final int MAX_STRING_SIZE = 1024;
    public static final int MAX_PAYLOAD_SIZE = 0xFFFF;
    public static final int MAX_URI_SIZE = 4096;
    public static final byte C2S_HELLO = 1; // [i32 version,<static utf-8>, <bool has group>, <secret utf-8>, <desc utf-8>]
    public static final byte S2C_AUTH_STATUS = 2; //[<byte 1 = success>, <utf-8 token>]
    public static final byte S2C_GET = 3; //[i32 uid,utf-8]
    // compress type
    // 0 - uncompressed
    // -1 - has no payload
    // >0 uncompressed size
    public static final byte C2S_RESPONSE = 4; //[i32 uid,i32 compress type, RAW utf-8 bytes]
    public static final byte S2C_KEEPALIVE_PING = 5; // []
    public static final byte C2S_KEEPALIVE_PONG = 6; // []
    public static final byte C2S_ERROR_MSG = 7; // [bool fatal, utf-8]


    public static String readUtf8(ByteBuffer buf, int maxSize) {
        int size = buf.getInt();
        if (size <= 0 || size >= maxSize) {
            throw new IllegalArgumentException("string max size " + maxSize + " but got " + size);
        }
        byte[] array = new byte[size];
        buf.get(array);
        return new String(array, StandardCharsets.UTF_8);
    }

    public static void writeUtf8(ByteBuffer buf, int maxSize, String value) {
       writeUtf8(buf, maxSize, value.getBytes(StandardCharsets.UTF_8));
    }
    public static void writeUtf8(ByteBuffer buf, int maxSize, byte[] array) {
        if (array.length > maxSize) {
            throw new IllegalArgumentException("string max size " + maxSize + " but got " + array.length);
        }
        buf.putInt(array.length);
        buf.put(array);
    }
}
