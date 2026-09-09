package dev.by1337.web.client;

public class WebProtocol {
    public static final int MAX_PAYLOAD_SIZE = 0xFFFF;
    public static final byte HELLO = 1; // [utf-8]
    public static final byte AUTH_STATUS = 2; //[<byte 1 = success>, <if 1 utf-8 url path>]
    public static final byte GET = 3; //[i32 uid,utf-8]
    public static final byte RESPONSE = 4; //[i32 uid,i32 uncompressed size,utf-8]

}
