package dev.by1337.web.client;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

final class BufCompressor {
    private final Deflater deflater;

    BufCompressor(int level) {
        this.deflater = new Deflater(level);
    }
    
    public void deflate(ByteBuffer source, ByteBuffer destination) {
        this.deflater.setInput(source);
        this.deflater.finish();

        while(!this.deflater.finished()) {
            this.deflater.deflate(destination);
        }
        
        this.deflater.reset();
    }
    public void deflate(byte[] input, int off, int len, ByteBuffer destination) {
        this.deflater.setInput(input, off, len);
        this.deflater.finish();

        while(!this.deflater.finished()) {
            this.deflater.deflate(destination);
        }

        this.deflater.reset();
    }

    public void close() {
        this.deflater.end();
    }
}
