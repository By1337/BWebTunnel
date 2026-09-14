package dev.by1337.web.network.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.stream.JsonReader;
import dev.by1337.web.ClientList;
import dev.by1337.web.db.Database;
import dev.by1337.web.db.User;
import dev.by1337.web.network.HttpResponser;
import dev.by1337.web.util.RequestRateLimiter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class AuthHandler {
    private final ClientList clientList;
    private final Database database;
    private final Cache<String, String> sessions = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofDays(1))
            .build();
    private final RequestRateLimiter limiter = new RequestRateLimiter(20, Duration.ofMinutes(1));

    public AuthHandler(ClientList clientList, Database database) {
        this.clientList = clientList;
        this.database = database;
    }

    public @Nullable String getSecret(FullHttpRequest req){
        var session = cookieValue(req, "session");
        if (session == null) return null;
        return sessions.getIfPresent(session);
    }

    public void on(ChannelHandlerContext ctx, FullHttpRequest req) {
        HttpResponser resp = new HttpResponser(ctx);
        String path = cleanupPath(req.uri());

        switch (path) {
            case "/session/login" -> handleLogin(ctx,req, resp);
            case "/session/me" -> handleMe(req, resp);
            case "/session/register" -> handleRegister(ctx,req, resp);
            case "/session/logout" -> handleLogout(req, resp);
            default -> resp.send(HttpResponseStatus.NOT_FOUND);
        }
    }

    public static String cleanupPath(String in) {
        if (in.isBlank()) return "/";

        if (!in.startsWith("/"))
            in = '/' + in;

        if (in.length() > 1 && in.endsWith("/"))
            in = in.substring(0, in.length() - 1);

        return in;
    }

    private void handleMe(FullHttpRequest req, HttpResponser resp) {
        if (req.method() != HttpMethod.GET) {
            resp.send(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        var session = cookieValue(req, "session");
        if (session == null || sessions.getIfPresent(session) == null) {
            resp.sendJsonStatus(HttpResponseStatus.UNAUTHORIZED, "{\"ok\":false}");
        } else {
            resp.sendJsonStatus(HttpResponseStatus.OK, "{\"ok\":true}");
        }
    }

    private void handleLogout(FullHttpRequest req, HttpResponser resp) {
        if (req.method() != HttpMethod.POST) {
            resp.send(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        var session = cookieValue(req, "session");
        if (session != null) {
            sessions.invalidate(session);
        }
        resp.send(HttpResponseStatus.OK);
    }

    private void handleLogin(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponser resp) {
        if (req.method() != HttpMethod.POST) {
            resp.send(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        if (limiter.isRateLimited(ctx, req)){
            resp.send(HttpResponseStatus.TOO_MANY_REQUESTS);
            return;
        }
        LoginData f = parse(req);
        if (f == null) {
            limiter.record(ctx, req);
            resp.sendJsonStatus(HttpResponseStatus.UNAUTHORIZED, "{\"ok\":false,\"message\": \"Неверный логин или пароль\"}");
            return;
        }
        var user = database.getUserByLogin(f.login);
        if (user == null || !user.testPassword(f.password)) {
            limiter.record(ctx, req);
            resp.sendJsonStatus(HttpResponseStatus.UNAUTHORIZED, "{\"ok\":false,\"message\": \"Неверный логин или пароль\"}");
            return;
        }
        String session;
        for (; ; ) {
            if (sessions.getIfPresent(session = ClientList.gen256Token()) == null) break;
        }
        sessions.put(session, user.secret);
        resp.sendJsonWithCookie(
                "{\"ok\":true}",
                "session",
                session,
                CookieBuilder.session(false)
        );
    }

    private void handleRegister(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponser resp) {
        if (req.method() != HttpMethod.POST) {
            resp.send(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        if (limiter.isRateLimited(ctx, req)){
            resp.send(HttpResponseStatus.TOO_MANY_REQUESTS);
            return;
        }
        limiter.record(ctx, req);
        LoginData f = parse(req);
        if (f == null) {
            resp.sendJsonStatus(HttpResponseStatus.UNAUTHORIZED, "{\"ok\":false,\"message\": \"пупупу\"}");
            return;
        }
        User user = new User(f.login, f.password);
        if (!database.addUser(user)) {
            resp.sendJsonStatus(HttpResponseStatus.UNAUTHORIZED, "{\"ok\":false,\"message\": \"Этот login уже занят\"}");
            return;
        }
        String session;
        for (; ; ) {
            if (sessions.getIfPresent(session = ClientList.gen256Token()) == null) break;
        }
        sessions.put(session, user.secret);
        resp.sendJsonWithCookie(
                "{\"ok\":true}",
                "session",
                session,
                CookieBuilder.session(false)
        );
    }

    private @Nullable LoginData parse(FullHttpRequest req) {
        var buf = req.content();
        int size = buf.readableBytes();
        if (size == 0 || size > 2048) return null;

        byte[] arr = new byte[size];
        buf.getBytes(buf.readerIndex(), arr);

        try (JsonReader reader = new JsonReader(
                new StringReader(new String(arr, StandardCharsets.UTF_8)))) {

            reader.beginObject();

            String login = null;
            String password = null;

            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "login" -> login = reader.nextString();
                    case "password" -> password = reader.nextString();
                    default -> reader.skipValue();
                }
            }

            reader.endObject();

            if (login == null || password == null) return null;
            return new LoginData(login, password);
        } catch (Exception e) {
            return null;
        }
    }

    private record LoginData(String login, String password) {

    }

    public static String cookieValue(FullHttpRequest req, String name) {
        String header = req.headers().get(HttpHeaderNames.COOKIE);
        if (header == null) return null;
        for (io.netty.handler.codec.http.cookie.Cookie c : ServerCookieDecoder.LAX.decode(header)) {
            if (c.name().equals(name)) return c.value();
        }
        return null;
    }
}