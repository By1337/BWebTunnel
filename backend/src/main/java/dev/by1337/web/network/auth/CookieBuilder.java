package dev.by1337.web.network.auth;

public class CookieBuilder {
    private boolean httpOnly = true, secure;
    private String sameSite = "Lax", path = "/";
    private long maxAge = 86400;

    public static CookieBuilder session(boolean https) {
        CookieBuilder b = new CookieBuilder();
        b.secure = https;
        return b;
    }

    public CookieBuilder maxAge(long s) {
        this.maxAge = s;
        return this;
    }

    public String build(String n, String v) {
        StringBuilder sb = new StringBuilder()
                .append(n).append('=').append(v)
                .append("; Path=").append(path)
                .append("; Max-Age=").append(maxAge)
                .append("; SameSite=").append(sameSite);
        if (httpOnly) sb.append("; HttpOnly");
        if (secure) sb.append("; Secure");
        return sb.toString();
    }
}