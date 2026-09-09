package dev.by1337.web.client;

public class WebProtocol {
    public static final byte HELLO = 1; // [utf-8]
    public static final byte AUTH_STATUS = 2; //[<byte 1 = success>, <if 1 utf-8 url path>]
}
