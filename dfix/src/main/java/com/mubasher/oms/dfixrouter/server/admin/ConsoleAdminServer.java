/**
 * JAYALAL KAHANDAWA
 * Sep 3, 2007
 * STPAdmin.java
 */
package com.mubasher.oms.dfixrouter.server.admin;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.system.Settings;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConsoleAdminServer extends Thread {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.admin.ConsoleAdminServer");
    private int dfixAdminPort = 9976;
    private ServerSocket serverSocket = null;
    private static ArrayList<ConsoleAdminClientProxy> consoleAdminClientProxies = new ArrayList<>(); //static list to keep track of all active telnet connections
    // Set of allowed IP addresses
    private static final Set<String> ALLOWED_IPS = new HashSet<>();

    public ConsoleAdminServer() {
        if (Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_PORT).length() > 0) {
            dfixAdminPort = Integer.parseInt(Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_PORT));
        }
        String consoleAdminAllowedIps = Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_ALLOWED_IPS);
        if (consoleAdminAllowedIps != null && consoleAdminAllowedIps.length() > 0) {
            ALLOWED_IPS.addAll(Arrays.asList(consoleAdminAllowedIps.split(",")));
            if (ALLOWED_IPS.contains(IConstants.LOCALHOST) || ALLOWED_IPS.contains(IConstants.LOCALHOST.toUpperCase()) || ALLOWED_IPS.contains(IConstants.IPV6_LOOPBACK) || ALLOWED_IPS.contains(IConstants.DEFAULT_IP)) {
                ALLOWED_IPS.add(IConstants.DEFAULT_IP); // IPv4 loopback address
                ALLOWED_IPS.add(IConstants.IPV6_LOOPBACK); // IPv6 loopback address
                ALLOWED_IPS.add(IConstants.LOCALHOST);
            }
            logger.debug("Starting Console Admin Server on port : " + dfixAdminPort+" Allowed Ips: "+ALLOWED_IPS);
        } else {
            logger.debug("Starting Console Admin Server on port : " + dfixAdminPort + " Allowed Ips: Any");
        }
        this.setPriority(Thread.NORM_PRIORITY);
    }

    @Override
    public void run() {
        initializeServerSocket();
        while (true) {
            establishConnection();
        }
    }

    private void initializeServerSocket() {
        if (serverSocket == null) {
            try {
                String consoleAdminBindIp = Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_BIND_IP);
                if(consoleAdminBindIp!=null && consoleAdminBindIp.length()>0) {
                    serverSocket = new ServerSocket(dfixAdminPort, 50, InetAddress.getByName(consoleAdminBindIp));
                } else {
                    serverSocket = new ServerSocket(dfixAdminPort);
                }
                logger.debug("Admin Server Console Connection Socket is Ready! Listening on : " + serverSocket.getLocalSocketAddress());
            } catch (Exception e) {
                logger.error("Error initializing the Console Socket.", e);
                serverSocket = null;
            }
        }
    }

    private void establishConnection() {
        ConsoleAdminClientProxy client;
        Socket socket;
        if (serverSocket == null) {
            return;
        }
        try {
            socket = serverSocket.accept();
            final String clientIpAddress = socket.getInetAddress().getHostAddress();
            if (!ALLOWED_IPS.isEmpty() && !ALLOWED_IPS.contains(clientIpAddress)) {
                logger.error("Client Ip(" + clientIpAddress + ") not in Allowed List. Allowed ips (" + ALLOWED_IPS + ")");
                PrintWriter writer = new PrintWriter(socket.getOutputStream());
                writer.write("Client Ip(" + clientIpAddress + ") not Allowed");
                writer.flush();
                writer.close();
                socket.close();
                return;
            }
            client = new ConsoleAdminClientProxy(socket);
            consoleAdminClientProxies.add(client);
            client.start();
        } catch (Exception e) {
            logger.error("Error initializing the Console Socket.", e);
        }
    }

    //Forcefully close all active telnet connections.
    protected static void closeAllConsoleAdminConnections() {
        consoleAdminClientProxies.forEach(client -> client.closeConnectionWithMessage("Forcefully closed by another telnet session"));
    }
}
