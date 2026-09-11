package dev.by1337.web.network.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.stream.JsonReader;
import dev.by1337.web.ClientList;
import dev.by1337.web.db.Database;
import dev.by1337.web.db.User;
import dev.by1337.web.network.HttpResponser;
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
            case "/session/login" -> handleLogin(req, resp);
            case "/session/me" -> handleMe(req, resp);
            case "/session/register" -> handleRegister(req, resp);
            case "/session/logout" -> handleLogout(req, resp);
            default -> resp.send(HttpResponseStatus.NOT_FOUND);
        }
    }

    public static String cleanupPath(String in) {
        if (in.isBlank()) return "/";
        if (!in.startsWith("/")) in = '/' + in;
        if (in.endsWith("/")) return in.substring(0, in.length() - 1);
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
    }

    private void handleLogin(FullHttpRequest req, HttpResponser resp) {
        if (req.method() != HttpMethod.POST) {
            resp.send(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
        LoginData f = parse(req);
        if (f == null) {
            resp.sendJsonStatus(HttpResponseStatus.UNAUTHORIZED, "{\"ok\":false,\"message\": \"Неверный логин или пароль\"}");
            return;
        }
        var user = database.getUserByLogin(f.login);
        if (user == null || !user.testPassword(f.password)) {
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

    private void handleRegister(FullHttpRequest req, HttpResponser resp) {
        if (req.method() != HttpMethod.POST) {
            resp.send(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }
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
        buf.readBytes(arr);
        JsonReader reader = new JsonReader(new StringReader(new String(arr, StandardCharsets.UTF_8)));
        try {
            reader.beginObject();
            String login = null;
            String password = null;

            if (!reader.hasNext()) return null;
            if ("login".equals(reader.nextName()))
                login = reader.nextString();
            else password = reader.nextString();
            if (!reader.hasNext()) return null;
            if ("password".equals(reader.nextName()))
                password = reader.nextString();
            else login = reader.nextString();

            if (login == null || password == null) return null;
            return new LoginData(login, password);
        } catch (Exception e) {
            return null;
        }
    }

    private record LoginData(String login, String password) {

    }

    /*private void handleRegister(FullHttpRequest req, HttpResponser resp) {
        Map<String, String> f = parseForm(req);
        String user = f.getOrDefault("username", "").trim();
        String pass = f.getOrDefault("password", "");

        if (user.length() < 3 || pass.length() < 8) {
            resp.sendJsonStatus(HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Логин ≥3, пароль ≥8\"}");
            return;
        }
        if (auth.exists(user)) {
            resp.sendJsonStatus(HttpResponseStatus.CONFLICT, "{\"error\":\"Уже занят\"}");
            return;
        }
        User u = auth.register(user, pass);
        resp.sendJsonWithCookie("{\"ok\":true}", "session", sessions.create(u.id()), cookieOpts(req));
    }

    private void handleLogout(FullHttpRequest req, HttpResponser resp) {
        String sid = cookieValue(req, "session");
        if (sid != null) sessions.destroy(sid);
        resp.sendJsonWithCookie("{\"ok\":true}", "session", "", cookieOpts(req).maxAge(0));
    }*/


    private CookieBuilder cookieOpts(FullHttpRequest req) {
        boolean https = "https".equalsIgnoreCase(req.headers().get("X-Forwarded-Proto"));
        return CookieBuilder.session(https);
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
// async function post(path, data) {
//  const res = await fetch(path, {
//    method: 'POST',
//    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
//    body: new URLSearchParams(data),
//    credentials: 'same-origin',   // ← обязательно, иначе Set-Cookie не примется
//  });
//  const json = await res.json();
//  return { ok: res.ok, status: res.status, json };
//}
//
/// / логин
//async function login(username, password) {
//  const { ok, json } = await post('/auth/login', { username, password });
//  if (ok) location.href = '/';
//  else showError(json.error);
//}
//
/// / регистрация
//async function register(username, password) {
//  const { ok, json } = await post('/auth/register', { username, password });
//  if (ok) location.href = '/';
//  else showError(json.error);
//}
//
/// / логаут
//async function logout() {
//  await post('/auth/logout', {});
//  location.href = '/login.html';
//}