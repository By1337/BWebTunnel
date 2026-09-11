package dev.by1337.web.db;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

public class User {
    public final String login;
    public final String passwordSha256;
    public final String secret;

    public User(String login, String passwordSha256) {
        this.login = login;
        this.passwordSha256 = passwordSha256;
        secret = Hashing.sha256().hashBytes((login+passwordSha256).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public boolean testPassword(String raw){
        return passwordSha256.equals(Hashing.sha256().hashBytes(raw.getBytes(StandardCharsets.UTF_8)).toString());
    }
}
