package dev.by1337.web.db;

import dev.by1337.web.ClientList;
import dev.by1337.web.util.Argon2idUtil;

public class User {
    public final String login;
    public final String passwordHash;
    public final String secret;

    public User(String login, String password) {
        this.login = login;
        this.passwordHash = Argon2idUtil.hash(password);
        secret = login + ":" + ClientList.gen128Token();

    }

    public boolean testPassword(String raw){
        return Argon2idUtil.verify(raw, passwordHash);
    }
}
