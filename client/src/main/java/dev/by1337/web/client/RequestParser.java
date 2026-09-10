package dev.by1337.web.client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RequestParser {
    // ?v=a=b=c -> 'v' = 'a=b=c'
    // ?raw&beta&id=5 -> 'raw' = '', 'beta' = '', 'id' = '5'
    // ?a=&b= -> 'a' = '', 'b' = ''
    // ?=value1&=value2 -> '' = ['value1', 'value2']
    // ?id=1&id=2&id=3 -> 'id' = ['1', '2', '3']
    // ?text=hello+world -> 'text' = 'hello world'
    // ?path+name=c%2B%2B -> 'path name' = 'c++'
    // ?symbols=%21%40%23%24 -> 'symbols' = '!@#$'
    // ?lang=%D1%80%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9 -> 'lang' = 'русский'
    // ?mix=a%D0%B1+c -> 'mix' = 'аб c'
    // ?raw&beta -> 'raw' = '', 'beta' = ''
    public static RequestParams parse(String input) throws ParseException {
        if (input.isBlank()) throw new ParseException("input is empty!", 0);
        ExpReader reader = new ExpReader(input);

        ByteBuffer buf = ByteBuffer.allocate(input.length() * 3);
        Map<String, List<String>> argsMap = new HashMap<>();
        String key = null;
        String value = null;
        String route = null;

        boolean isParams = false;
        boolean inValue = false;
        char c;
        while (true) {
            c = reader.next();
            if (isParams) {
                if (c == '%') {
                    buf.put(decodeByte(reader));
                } else if (c == '+') {
                    putChar(buf, reader, ' ');
                } else if (c == '=' && !inValue) {
                    buf.flip();
                    if (!buf.hasRemaining())
                        key = "";
                    byte[] arr = new byte[buf.remaining()];
                    buf.get(arr);
                    key = new String(arr, StandardCharsets.UTF_8);
                    buf.clear();
                    inValue = true;
                } else if (c == '&' || c == '\0') {
                    String current;
                    buf.flip();
                    if (buf.hasRemaining()) {
                        byte[] arr = new byte[buf.remaining()];
                        buf.get(arr);
                        current = new String(arr, StandardCharsets.UTF_8);
                    } else {
                        current = "";
                    }
                    if (key == null) {
                        key = current;
                        value = "";
                    } else {
                        value = current;
                    }
                    buf.clear();
                    argsMap.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                    inValue = false;
                    key = null;
                    if (c == '\0') {
                        break;
                    }
                } else {
                    putChar(buf, reader, c);
                }
            } else {
                if (c == '/' && buf.position() != 0) {
                    putChar(buf, reader, '/');
                } else if (c == '%') {
                    buf.put(decodeByte(reader));
                } else if (c == '?' || c == '\0') {
                    isParams = true;
                    buf.flip();
                    if (buf.hasRemaining()){
                        byte[] arr = new byte[buf.remaining()];
                        buf.get(arr);
                        route = new String(arr, StandardCharsets.UTF_8);
                    }
                    if (c == '\0') break;
                    buf.clear();
                }  else {
                    putChar(buf, reader, c);
                }
            }
        }
        return new RequestParams(route, argsMap);
    }

    private static void putChar(ByteBuffer buf, ExpReader reader, char c) throws ParseException {
        if (Character.isHighSurrogate(c)) {
            char low = reader.next();
            if (!Character.isLowSurrogate(low)) {
                reader.throwBadInput();
            }
            putCodePoint(buf, Character.toCodePoint(c, low));
            return;
        }

        if (Character.isLowSurrogate(c)) {
            reader.throwBadInput();
        }

        putCodePoint(buf, c);
    }

    private static void putCodePoint(ByteBuffer buf, int c) {
        if (c <= 0x7F) {
            buf.put((byte) c);
        } else if (c <= 0x7FF) {
            buf.put((byte) (0xC0 | (c >>> 6)));
            buf.put((byte) (0x80 | (c & 0x3F)));
        } else if (c <= 0xFFFF) {
            buf.put((byte) (0xE0 | (c >>> 12)));
            buf.put((byte) (0x80 | ((c >>> 6) & 0x3F)));
            buf.put((byte) (0x80 | (c & 0x3F)));
        } else {
            buf.put((byte) (0xF0 | (c >>> 18)));
            buf.put((byte) (0x80 | ((c >>> 12) & 0x3F)));
            buf.put((byte) (0x80 | ((c >>> 6) & 0x3F)));
            buf.put((byte) (0x80 | (c & 0x3F)));
        }
    }

    private static byte decodeByte(ExpReader reader) throws ParseException {
        int v = 0;
        int x;
        if ((x = hexToInt(reader.next())) == -1) reader.throwBadInput();
        v = (v << 0) | x;
        if ((x = hexToInt(reader.next())) == -1) reader.throwBadInput();
        v = (v << 4) | x;
        return (byte) v;
    }

    private static int hexToInt(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }
}
