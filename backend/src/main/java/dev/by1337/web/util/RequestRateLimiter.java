package dev.by1337.web.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.net.InetSocketAddress;
import java.time.Duration;

public final class RequestRateLimiter {

    private final Cache<String, Window> cache;

    private final int maxFailures;
    private final long windowNanos;

    public RequestRateLimiter(int maxFailures, Duration window) {
        this.maxFailures = maxFailures;
        this.windowNanos = window.toNanos();

        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(window)
                .maximumSize(100_000)
                .build();
    }

    public boolean isRateLimited(ChannelHandlerContext ctx, FullHttpRequest request) {
        return isRateLimited(getClientIp(ctx, request));
    }

    public boolean isRateLimited(String ip) {
        long now = System.nanoTime();

        Window window = cache.get(ip, k -> new Window(now));

        if (now - window.start >= windowNanos) {
            window.start = now;
            window.failures = 0;
        }

        return window.failures >= maxFailures;
    }

    public void record(ChannelHandlerContext ctx, FullHttpRequest request) {
        record(getClientIp(ctx, request));
    }
    public void record(String ip) {
        long now = System.nanoTime();

        Window window = cache.get(ip, k -> new Window(now));

        if (now - window.start >= windowNanos) {
            window.start = now;
            window.failures = 0;
        }

        window.failures++;
    }

    public void success(String ip) {
        cache.invalidate(ip);
    }

    private static String getClientIp(ChannelHandlerContext ctx, FullHttpRequest request) {
        String realIp = request.headers().get("X-Real-IP");
        if (realIp != null) {
            return realIp;
        }

        return ((InetSocketAddress) ctx.channel().remoteAddress())
                .getAddress()
                .getHostAddress();
    }

    private static final class Window {
        long start;
        int failures;

        Window(long start) {
            this.start = start;
        }
    }
}