package com.mubasher.oms.dfixrouter.server.admin;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.SSLHandler;
import com.mubasher.oms.dfixrouter.system.Settings;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.*;

public class UIAdminServer extends Thread {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.admin.UIAdminServer");
    private static HashMap<String, UIAdminClientProxy> clients = new HashMap<>();
    private static ServerSocket serverSocket;
    private static int clientCount = 1;
    // Set of allowed IP addresses
    private static final Set<String> ALLOWED_IPS = new HashSet<>();

    public UIAdminServer() {
        logger.debug("Starting Admin Server....");
        this.setPriority(Thread.NORM_PRIORITY + 3);
    }

    public static void removeClient(int i) {
        clients.remove(Integer.toString(i));
    }

    public static void sendToAdminClients(String exchange, String message) {
        Iterator<UIAdminClientProxy> iter = clients.values().iterator();
        while (iter.hasNext()) {
            UIAdminClientProxy c = iter.next();
            try {
                c.sendToAdminClient(exchange, message);
            } catch (Exception e) {
                logger.error("Error at send msg to admin clients: " + e.getMessage(), e);
            }
        }
    }

    public static Map<String, UIAdminClientProxy> getClients() {
        return clients;
    }

    @Override
    public void run() {
        if (Settings.getProperty(SettingsConstants.ADMIN_PORT)!=null) {
            initializeServerSocket();
            while (DFIXRouterManager.getInstance().isStarted()) {
                establishConnection();
            }
        } else {
            logger.warn("UIAdminServer not running Configure ADMIN_PORT to run UIAdminServer ");
        }
    }

    private static void initializeServerSocket() {
        while (serverSocket == null) {
            try {
                int iPort = Integer.parseInt(Settings.getProperty(SettingsConstants.ADMIN_PORT));
                String uiAdminAllowedIps = Settings.getProperty(SettingsConstants.ADMIN_ALLOWED_IPS);
                String uiAdminBindIp = Settings.getProperty(SettingsConstants.ADMIN_BIND_IP);
                if (uiAdminAllowedIps != null && uiAdminAllowedIps.length() > 0) {
                    ALLOWED_IPS.addAll(Arrays.asList(uiAdminAllowedIps.split(",")));
                    if (ALLOWED_IPS.contains(IConstants.LOCALHOST) || ALLOWED_IPS.contains(IConstants.LOCALHOST.toUpperCase()) || ALLOWED_IPS.contains(IConstants.IPV6_LOOPBACK) || ALLOWED_IPS.contains(IConstants.DEFAULT_IP)) {
                        ALLOWED_IPS.add(IConstants.DEFAULT_IP); // IPv4 loopback address
                        ALLOWED_IPS.add(IConstants.IPV6_LOOPBACK); // IPv6 loopback address
                        ALLOWED_IPS.add(IConstants.LOCALHOST);
                    }
                    logger.debug("Starting Admin Server , Allowed Ips : " + ALLOWED_IPS);
                } else {
                    logger.debug("Starting Admin Server , Allowed Ips : Any");
                }
                initServerSocket(uiAdminBindIp, iPort);
            } catch (Exception e) {
                serverSocket = null;
                logger.error(e.getMessage(), e);
                logger.error("Error initializing the FIX Manager Connection Socket... " + (IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.ADMIN_SSL_ENABLED))?" [SSL] ":"") + e);
                sleepThread(5000);
            }
        }
    }

    private static void initServerSocket(String uiAdminBindIp, int iPort) throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException, DFIXConfigException, KeyManagementException {
        if (IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.ADMIN_SSL_ENABLED))) {
            SSLServerSocketFactory ssf = SSLHandler.getSSLServerSocketFactory(System.getProperty("javax.net.ssl.TrustManagerFactory.algorithm", TrustManagerFactory.getDefaultAlgorithm()));
            if (uiAdminBindIp != null && uiAdminBindIp.length() > 0) {
                serverSocket = ssf.createServerSocket(iPort, 50, InetAddress.getByName(uiAdminBindIp));
            } else {
                serverSocket = ssf.createServerSocket(iPort);
            }
            logger.debug("Admin Server SSL Connection Socket is Ready! Listening on : " + serverSocket.getLocalSocketAddress());
        } else {
            if(uiAdminBindIp !=null && uiAdminBindIp.length()>0) {
                serverSocket = new ServerSocket(iPort, 50, InetAddress.getByName(uiAdminBindIp));
            } else {
                serverSocket = new ServerSocket(iPort);
            }
            logger.debug("Admin Server Connection Socket is Ready (SSL Disabled)! Listening on : " + serverSocket.getLocalSocketAddress());
        }
    }

    private static void establishConnection() {

        UIAdminClientProxy client;
        Socket socket;

        if (serverSocket == null)
            return;

        try {
            socket = serverSocket.accept();
            final String clientIpAddress = socket.getInetAddress().getHostAddress();
            if (!ALLOWED_IPS.isEmpty() && !ALLOWED_IPS.contains(clientIpAddress)) {
                logger.error("Client Ip("+ clientIpAddress +") not in Allowed List. Allowed ips ("+ALLOWED_IPS+")");
                PrintWriter writer = new PrintWriter(socket.getOutputStream());
                writer.write("Client Ip(" + clientIpAddress + ") not Allowed");
                writer.flush();
                writer.close();
                socket.close();
                return;
            }
            logger.debug("Monitor Client Connected : " + socket.getInetAddress().toString());
            client = new UIAdminClientProxy(socket, clientCount++);
            client.start();
            clients.put(Integer.toString(client.getMyID()), client);
            sleepThread(1000);

        } catch (IOException e) {
            logger.error("Error connecting Monitor Client to Admin Server... " + e);
        }
    }

    private static void sleepThread(int iTime) {
        try {
            Thread.sleep(iTime);
        } catch (Exception e) {
            logger.error("Exception at UIAdminServer sleep thread: " + e.getMessage() + e);
        }
    }
}
