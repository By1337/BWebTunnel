package dev.by1337.web.util;

import io.netty.buffer.ByteBuf;

public final class StreamJsonWriter {

    private final ByteBuf buf;

    public StreamJsonWriter(ByteBuf buf) {
        this.buf = buf;
    }

    public void startArray() {
        writeChar('[');
    }

    public void endArray() {
        writeChar(']');
    }

    public void appendComma(){
        writeChar(',');
    }

    public void startObject() {
        writeChar('{');
    }

    public void endObject() {
        writeChar('}');
    }

    public void appendField(String name, String value) {
        string(name);
        writeChar(':');
        string(value);
    }


    public void string(String value) {
        writeChar('"');

        CharIterator it = new CharIterator(value);
        while (it.hasNext()){
            char c = it.next();
            switch (c) {
                case '"'  -> writeChars('\\', '"');
                case '\\' -> writeChars('\\', '\\');
                case '\n' -> writeChars('\\', 'n');
                case '\r' -> writeChars('\\', 'r');
                case '\t' -> writeChars('\\', 't');
                default -> writeChar(c, it);
            }
        }
        writeChar('"');
    }

    private void writeCodePoint(int c) {
        if (c <= 0x7F) {
            buf.writeByte((byte) c);
        } else if (c <= 0x7FF) {
            buf.writeByte((byte) (0xC0 | (c >>> 6)));
            buf.writeByte((byte) (0x80 | (c & 0x3F)));
        } else if (c <= 0xFFFF) {
            buf.writeByte((byte) (0xE0 | (c >>> 12)));
            buf.writeByte((byte) (0x80 | ((c >>> 6) & 0x3F)));
            buf.writeByte((byte) (0x80 | (c & 0x3F)));
        } else {
            buf.writeByte((byte) (0xF0 | (c >>> 18)));
            buf.writeByte((byte) (0x80 | ((c >>> 12) & 0x3F)));
            buf.writeByte((byte) (0x80 | ((c >>> 6) & 0x3F)));
            buf.writeByte((byte) (0x80 | (c & 0x3F)));
        }
    }
    private void writeChars(char c, char c1) {
        writeChar(c);
        writeChar(c1);
    }
    public void writeChar(char c) {
        writeCodePoint(c);
    }
    private void writeChar(char c, CharIterator it) {
        if (Character.isHighSurrogate(c)) {
            if (!it.hasNext()) throw new IllegalArgumentException("EOF but expected low surrogate" );
            char low = it.next();
            if (!Character.isLowSurrogate(low)) {
                throw new IllegalArgumentException("expected low surrogate got " + (int)low);
            }
            writeCodePoint(Character.toCodePoint(c, low));
            return;
        }

        if (Character.isLowSurrogate(c)) {
            throw new IllegalArgumentException();
        }

        writeCodePoint(c);
    }
    private static class CharIterator{
        private final String value;
        private final int size;
        private int pos;

        private CharIterator(String value) {
            this.value = value;
            size = value.length();
        }
        public char next(){
            return value.charAt(pos++);
        }
        public boolean hasNext(){
            return pos < size;
        }
    }
}