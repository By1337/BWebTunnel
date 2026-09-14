package dev.by1337.web;

import dev.by1337.web.db.Database;
import dev.by1337.web.db.FileDatabase;
import dev.by1337.web.network.ConnectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Main {


    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        Database database = new FileDatabase("./users.json");

        ClientList clientList = new ClientList();

        int port;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 4443;
        }

        ConnectionListener connectionListener = new ConnectionListener(clientList, null, database);
        connectionListener.startServerListener(port);// //"ws://localhost:4443/api/ws"
    }
}
