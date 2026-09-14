package dev.by1337.web.client;


import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

public final class StreamJsonWriter {

    private byte[] buffer = new byte[2048];
    private int position;
    private boolean hasElements;

    public ByteBuffer wrapNioBuffer() {
        return ByteBuffer.wrap(buffer, 0, position);
    }

    int position() {
        return position;
    }

    byte[] buffer() {
        return buffer;
    }

    private void ensure(int size) {
        if (buffer.length - position >= size) {
            return;
        }

        int required = position + size;
        int newSize = Math.max(buffer.length << 1, required);

        buffer = Arrays.copyOf(buffer, newSize);
    }

    public void putString(@Nullable String key, String value) {
        if (hasElements) appendComma();
        hasElements = true;
        if (key != null) {
            escapedString(key);
            writeChar(':');
        }
        escapedString(value);
    }

    public void putInt(@Nullable String key, int value) {
        if (hasElements) appendComma();
        hasElements = true;
        if (key != null) {
            escapedString(key);
            writeChar(':');
        }

        string(Integer.toString(value));
    }

    public void putLong(@Nullable String key, long value) {
        if (hasElements) appendComma();
        hasElements = true;
        if (key != null) {
            escapedString(key);
            writeChar(':');
        }
        string(Long.toString(value));
    }

    public void putDouble(@Nullable String key, double value) {
        if (hasElements) appendComma();
        hasElements = true;
        if (key != null) {
            escapedString(key);
            writeChar(':');
        }
        string(Double.toString(value));
    }

    public void putBoolean(@Nullable String key, boolean value) {
        if (hasElements) appendComma();
        hasElements = true;
        if (key != null) {
            escapedString(key);
            writeChar(':');
        }
        string(Boolean.toString(value));
    }

    public void object(@Nullable String key, Consumer<StreamJsonWriter> c) {
        try (var o = startObject(key)){
            c.accept(this);
        }
    }
    public void array(@Nullable String key, Consumer<StreamJsonWriter> c) {
        try (var o = startArray(key)){
            c.accept(this);
        }
    }

    public Scope startObject(@Nullable String key) {
        if (hasElements) appendComma();
        if (key != null) {
            escapedString(key);
            writeChar(':');
        }
        writeChar('{');
        hasElements = false;
        return () -> {
            writeChar('}');
            hasElements = true;
        };
    }

    public Scope startArray(@Nullable String key) {
        if (hasElements) appendComma();
        if (key != null) {
            escapedString(key);
            writeChar(':');
        }
        writeChar('[');
        hasElements = false;
        return () -> {
            writeChar(']');
            hasElements = true;
        };
    }

    private void appendComma() {
        writeChar(',');
    }

    private void string(String value) {
        var v = value.getBytes(StandardCharsets.UTF_8);
        ensure(v.length);
        System.arraycopy(v, 0, buffer, position, v.length);
        position += v.length;
    }

    private void escapedString(String value) {
        writeChar('"');

        CharIterator it = new CharIterator(value);
        while (it.hasNext()) {
            char c = it.next();
            switch (c) {
                case '"' -> writeChars('\\', '"');
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
            ensure(1);
            buffer[position++] = ((byte) c);
        } else if (c <= 0x7FF) {
            ensure(2);
            buffer[position++] = ((byte) (0xC0 | (c >>> 6)));
            buffer[position++] = ((byte) (0x80 | (c & 0x3F)));
        } else if (c <= 0xFFFF) {
            ensure(3);
            buffer[position++] = ((byte) (0xE0 | (c >>> 12)));
            buffer[position++] = ((byte) (0x80 | ((c >>> 6) & 0x3F)));
            buffer[position++] = ((byte) (0x80 | (c & 0x3F)));
        } else {
            ensure(4);
            buffer[position++] = ((byte) (0xF0 | (c >>> 18)));
            buffer[position++] = ((byte) (0x80 | ((c >>> 12) & 0x3F)));
            buffer[position++] = ((byte) (0x80 | ((c >>> 6) & 0x3F)));
            buffer[position++] = ((byte) (0x80 | (c & 0x3F)));
        }
    }

    private void writeChars(char c, char c1) {
        writeChar(c);
        writeChar(c1);
    }

    private void writeChar(char c) {
        writeCodePoint(c);
    }

    private void writeChar(char c, CharIterator it) {
        if (Character.isHighSurrogate(c)) {
            if (!it.hasNext()) throw new IllegalArgumentException("EOF but expected low surrogate");
            char low = it.next();
            if (!Character.isLowSurrogate(low)) {
                throw new IllegalArgumentException("expected low surrogate got " + (int) low);
            }
            writeCodePoint(Character.toCodePoint(c, low));
            return;
        }

        if (Character.isLowSurrogate(c)) {
            throw new IllegalArgumentException();
        }

        writeCodePoint(c);
    }

    private static class CharIterator {
        private final String value;
        private final int size;
        private int pos;

        private CharIterator(String value) {
            this.value = value;
            size = value.length();
        }

        public char next() {
            return value.charAt(pos++);
        }

        public boolean hasNext() {
            return pos < size;
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

}