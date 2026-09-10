package dev.by1337.web.client;

public class WebProtocol {
    public static final int MAX_PAYLOAD_SIZE = 0xFFFF;
    public static final byte HELLO = 1; // [i32 version,utf-8]
    public static final byte AUTH_STATUS = 2; //[<byte 1 = success>, <if 1 utf-8 token>]
    public static final byte GET = 3; //[i32 uid,utf-8]
    // compress type
    // 0 - uncompressed
    // -1 - has no payload
    // >0 uncompressed size
    public static final byte RESPONSE = 4; //[i32 uid,i32 compress type,utf-8]

}
