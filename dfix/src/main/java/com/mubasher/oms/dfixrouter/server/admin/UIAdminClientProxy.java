package com.mubasher.oms.dfixrouter.server.admin;

import com.mubasher.oms.dfixrouter.beans.UIAdminMessage;
import com.mubasher.oms.dfixrouter.constants.AdminCommands;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class UIAdminClientProxy extends Thread {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.admin.UIAdminClientProxy");
    private int myID = -1;
    private String clientIP;
    private AdminManager adminManager = AdminManager.getInstance();
    private Socket socket = null;
    private InputStream in = null;
    private OutputStream out = null;
    private boolean clientConnected = false;

    public UIAdminClientProxy(Socket socket, int myID) {

        try {
            this.myID = myID;
            this.socket = socket;
            clientIP = this.socket.getInetAddress().getHostAddress();
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
            clientConnected = true;
            logger.debug("Monitor Client Connected from " + clientIP);
        } catch (Exception e) {
            logger.error("Error Establishing Client Connection for " + socket.getInetAddress().toString());
            logger.error(e.getMessage(), e);
        }
    }

    public int getMyID() {
        return myID;
    }

    public void setMyID(int myID) {
        this.myID = myID;
    }

    @Override
    public void run() {
        logger.debug("Starting Monitor Thread...");
        while (clientConnected) {
            try {
                String data = readLine();
                logger.debug("starting Client Process777...");
                processMsg(data);
            } catch (Exception e) {
                logger.error("Exception at Monitor Thread: " + e.getMessage(), e);
                closeConnection();
            }
            sleepThread(100);
        }
    }

    private String readLine() throws IOException {
        StringBuilder builder = new StringBuilder("");
        if (in == null)
            throw new IOException();
        int i = in.read();
        logger.debug(Integer.toString(i));
        if (i == -1) {
            throw new IOException();
        }
        char ch = (char) i;
        int count = 0;
        while (ch != '\n') {
            count++;
            builder.append(ch);
            ch = (char) in.read();
            if (count > 500) {
                logger.debug("Invalid Frame Exception:" + clientIP + " - " + builder.toString());
                throw new IOException("Invalid Frame Exception");
            }
        }
        return builder.toString();
    }

    private void processMsg(String data) {
        logger.debug("Message received from Monitor Client=" + data);
        UIAdminMessage msg = new UIAdminMessage();
        msg.parseMessage(data);
        String session = msg.getExchange();
        String message = msg.getMessage();
        if (message.equalsIgnoreCase(AdminCommands.CONNECT.name())) {
            logger.info("Connect Session :" + session);
            adminManager.connectSession(session);
        } else if (message.equalsIgnoreCase(AdminCommands.DISCONNECT.name())) {
            logger.info("Disconnect Session :" + session);
            adminManager.disconnectSession(session);
        } else if (message.equalsIgnoreCase(AdminCommands.EOD.name())) {
            logger.info("Run EOD :" + session);
            adminManager.runEod(session);
        } else if (message.equalsIgnoreCase(AdminCommands.SEND_SESSION_LIST.name())) {
            sendToAdminClient(IConstants.ALL_SESSIONS, adminManager.sendSessionList());
        } else if (message.equalsIgnoreCase(AdminCommands.SEND_SESSIONS_SEQUENCE.name())) {
            String sesSequence = adminManager.sendSessionSequence();
            if (sesSequence != null) {
                sendToAdminClient(IConstants.ALL_SESSIONS, sesSequence);
            }
        } else if (message.equalsIgnoreCase(AdminCommands.EXIT_MONITOR.name())) {
            logger.info("Exit from FIX Monitor");
            closeConnection();
        } else {
            String[] data1 = message.split(IConstants.SS);
            if (data1[0].equalsIgnoreCase(AdminCommands.RESET_IN_SEQ.name())) {
                logger.info("reset in sequence :" + session + ":" + data1[1]);
                adminManager.resetInSequence(session, Integer.parseInt(data1[1]));
            } else if (data1[0].equalsIgnoreCase(AdminCommands.RESET_OUT_SEQ.name())) {
                logger.info("reset out sequence :" + session + ":" + data1[1]);
                adminManager.resetOutSequence(session, Integer.parseInt(data1[1]));
            }
        }
    }

    private void closeConnection() {
        UIAdminServer.removeClient(myID);
        try {
            in.close();
            out.close();
            socket.close();
        } catch (Exception e) {
            logger.error("Exception at Monitor Client Connection Close: " + e.getMessage(), e);
        }
        clientConnected = false;
        socket = null;
        in = null;
        out = null;
        logger.debug("Monitor Client Disconnected - " + clientIP);
    }

    private void sleepThread(int iTime) {
        try {
            Thread.sleep(iTime);
        } catch (Exception e) {
            logger.error("Exception at Monitor Client sleepThread: " + e.getMessage(), e);
        }
    }

    public void sendToAdminClient(String exchange, String message) {
        UIAdminMessage adminMsg = new UIAdminMessage();
        adminMsg.setExchange(exchange);
        adminMsg.setMessage(message);
        try {
            out.write(adminMsg.composeMessage().getBytes());
            out.flush();
            logger.debug("Send to Monitor:" + adminMsg.composeMessage());
        } catch (Exception e) {
            logger.error("Send to Monitor Failed:" + e.getMessage(), e);
        }
    }
}
