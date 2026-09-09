package dev.by1337.web;

import dev.by1337.web.network.ConnectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Main {


    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        ConnectionListener connectionListener = new ConnectionListener();
        connectionListener.startServerListener(4443);// //"ws://localhost:4443/api/ws"
    }

}
