package com.mubasher.oms.dfixrouter.server.admin;

import com.isi.security.GNUCrypt;
import com.mubasher.oms.dfixrouter.beans.TradingMarketBean;
import com.mubasher.oms.dfixrouter.constants.AdminCommands;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.server.fix.flowcontrol.FlowController;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.stores.TradingMarketStore;
import quickfix.*;

import java.io.*;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.util.List;

public class ConsoleAdminClientProxy extends Thread {
    private final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.admin.ConsoleAdminClientProxy");
    private final AdminManager adminManager = AdminManager.getInstance();
    private String dfixDescription = null;
    private Socket socket = null;
    private String clientIp = null;
    private BufferedReader reader = null;
    private PrintWriter writer = null;
    private boolean isFirst = true;
    private String lastCommand = "?";
    private static final String MESSAGE_CONST_PREFIX = "Message : ";
    private static final String DEFAULT_OUTPUT_STRING = "Usage: type ? for help";

    public ConsoleAdminClientProxy(Socket socket) {
        try {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(socket.getOutputStream());
            this.clientIp = socket.getInetAddress().getHostAddress();
        } catch (Exception e) {
            logger.error("Error Establishing Client Connection for "
                    + socket.getInetAddress().toString() + " - " + e.getMessage(), e);
        }
    }

    @Override
    public void run() {
        logger.debug("Starting Console Client Thread.");
        while (socket != null && socket.isConnected()) {
            try {
                if (isFirst) {
                    writeLine("DFIXRTR> Write Password and ENTER.");
                    String input = GNUCrypt.encrypt(IConstants.ENCRYPTION_KEY, readLine());
                    if (!input.equals(Settings.refreshAndGetPassword())) {
                        logger.error("Wrong password: " + input);
                        break;
                    }
                    writeLine("DFIXRTR> Welcome to DFIXRouter");
                    isFirst = false;
                    writeLine(processInput(AdminCommands.SHOW_STATUS.name()));
                }
                String input = readLine();
                String output = processInput(input.trim());
                writeLine(output);
            } catch (Exception e) {
                logger.error("Error at ConsoleAdminClientProxy: " + e.getMessage(), e);
                closeConnection();
            }
        }
        closeConnection();
    }

    private void writeLine(String message) {
        StringBuilder stringBuilder = new StringBuilder(message.replaceAll("\n", "\r\n"));//Telnet window Line break: \r\n
        if (!message.endsWith("\n")) {
            stringBuilder.append("\r\n");
        }
        if (!isFirst) {
            stringBuilder.append("DFIXRTR> ");
        }
        if (this.writer != null) {
            this.writer.write(stringBuilder.toString());
            this.writer.flush();
        }
    }

    private String processInput(String line) {
        if (line.trim().equals("[A")) {
            line = lastCommand;
        }
        String output;
        logger.info("Admin Message received to Console: " + line + "| From ClientIp: " + this.clientIp);
        output = processAdminCmds(line);
        lastCommand = line;
        return output;
    }

    String processAdminCmds(String line) {
        String output = DEFAULT_OUTPUT_STRING;
        try {
            String[] inArgs = line.split(",");
            switch (AdminCommands.valueOf(inArgs[0].trim().toUpperCase())) {
                case RELOAD:
                    output = processReloadCmd(inArgs);
                    break;
                case SHUTDOWN:
                    adminManager.stopDFIXRouter();
                    writeLine("Closing Application.");
                    closeConnection();
                    DFIXRouterManager.exitApplication(0);
                    break;
                case RESET_OUT_SEQ:
                    output = "\n" + adminManager.resetOutSequence(inArgs[1].trim(), Integer.parseInt((inArgs[2]).trim()));
                    break;
                case RESET_IN_SEQ:
                    output = "\n" + adminManager.resetInSequence(inArgs[1].trim(), Integer.parseInt((inArgs[2]).trim()));
                    break;
                case CONNECT:
                    output = adminManager.connectSession(inArgs[1].trim());
                    break;
                case DISCONNECT:
                    output = adminManager.disconnectSession(inArgs[1].trim());
                    break;
                case ACTIVATE:
                    output = adminManager.startDFIXRouter();
                    break;
                case PASSIVATE:
                    output = adminManager.stopDFIXRouter();
                    break;
                case EOD:
                    output = adminManager.runEod(inArgs[1].trim());
                    break;
                case SHOW_STATUS:
                    output = adminManager.showStatus();
                    break;
                case EXIT:
                case QUIT:
                    closeConnection();
                    break;
                case SET_MARKET_STATUS:
                    output = processSetMarketStatusCmd(inArgs);
                    break;
                case RELEASE:
                    output = processReleaseCmd(inArgs[1].trim());
                    break;
                case STORE_MESSAGE:
                    output = processStoreMessageCmd(inArgs);
                    break;
                case SEND_MESSAGE:
                    String strMessage1 = inArgs[2].trim();
                    FIXClient.getFIXClient().sendString(strMessage1, inArgs[1].trim());
                    output = MESSAGE_CONST_PREFIX + strMessage1 + " is sent to " + inArgs[1].trim();
                    break;
                case GET_MESSAGES:
                    output = processGetMessagesCmd(inArgs);
                    break;
                case CHANGE_PASSWORD:
                    output = processChangePasswordCmd();
                    break;
                default:
                    output = "Invalid Input" + output;
            }
        } catch (IllegalArgumentException e) {
            output = printDescription();
        } catch (Exception e) {
            logger.error("Error at processing Admin Command: " + e.getMessage(), e);
            output = e.getMessage() + "\n" + DEFAULT_OUTPUT_STRING;
        }
        return output;
    }

    private String processStoreMessageCmd(String[] inArgs) throws IOException {
        String strSeqNo = inArgs[2].trim();
        String strMessage = inArgs[3].trim();
        if (FIXClient.getFIXClient().storeMessage(FIXClient.getFIXClient().getSessionID(inArgs[1].trim()), Integer.parseInt(strSeqNo), strMessage)) {
            return MESSAGE_CONST_PREFIX + strMessage + " is stored in " + inArgs[1].trim();
        }
        return MESSAGE_CONST_PREFIX + strMessage + " is not stored in " + inArgs[1].trim();

    }

    private static String processGetMessagesCmd(String[] inArgs) throws IOException, InvalidMessage {
        String startSeqStr = inArgs[2].trim();
        String endSeqStr = inArgs[3].trim();
        StringBuilder sb = new StringBuilder();
        for (Message message : FIXClient.getFIXClient().getMessages(FIXClient.getFIXClient().getSessionID(inArgs[1].trim()), Integer.parseInt(startSeqStr), Integer.parseInt(endSeqStr))) {
            sb.append(message.toString()).append("\n");
        }
        return sb.toString();
    }

    private String processChangePasswordCmd() throws InvalidKeyException, IOException {
        String output;
        //only allow change_password from within the host machine
        if (clientIp.contains("127.0.0.1") || clientIp.contains("localhost")) {
            writeLine("DFIXRTR> Enter Old password");
            String oldPassword = readLine();
            if (Settings.getPassword().equals(GNUCrypt.encrypt(IConstants.ENCRYPTION_KEY, oldPassword))) {
                writeLine("DFIXRTR> Enter New password");
                String newPassword = readLine();
                writeLine("DFIXRTR> Confirm New password");
                String confirmNewPassword = readLine();
                if (newPassword.equals(confirmNewPassword)) {
                    Settings.setPassword(GNUCrypt.encrypt(IConstants.ENCRYPTION_KEY, newPassword));
                    output = "Password Updated ; Login Again to Continue";
                    writeLine(output);
                    ConsoleAdminServer.closeAllConsoleAdminConnections();
                } else {
                    output = "New password and Confirm password not match.\nretry with " + AdminCommands.CHANGE_PASSWORD.name() + " command again";
                }
            } else {
                output = "Wrong Old password : retry with " + AdminCommands.CHANGE_PASSWORD.name() + " command again";
            }
        } else {
            output = "Password change is only available from Host VM.";
        }
        return output;
    }

    private String processReleaseCmd(String sessionIdentifier) {
        SessionID sessionID = FIXClient.getFIXClient().getSessionID(sessionIdentifier);
        String output = "Session Identifier not valid: " + sessionIdentifier;
        if (FIXClient.getFIXClient().getFlowControllers().containsKey(sessionID)) {
            FlowController flowController = FIXClient.getFIXClient().getFlowControllers().get(sessionID);
            flowController.initiate();
            output = "Flow Control re-initiated to the session: " + sessionIdentifier;
        }
        return output;
    }

    private String processSetMarketStatusCmd(String[] inArgs) {
        String output = DEFAULT_OUTPUT_STRING;
        String exchange = inArgs[1].trim();
        String marketSegmentId = inArgs[2].trim();
        TradingMarketBean tradingMarketBean = TradingMarketStore.getTradingMarket(exchange, marketSegmentId);
        if (tradingMarketBean != null) {
            tradingMarketBean.setTradingSessionId(inArgs[3].trim().toUpperCase());
            TradingMarketStore.saveMarketStatus(tradingMarketBean);
        } else {
            output = "Trading Market with, Exchange: " + exchange + " MarketCode: " + marketSegmentId + " is not available";
        }
        return output;
    }

    private String processReloadCmd(String[] inArgs) throws ConfigError, IOException {
        String sessionIdentifier;
        String output = DEFAULT_OUTPUT_STRING;
        if (inArgs.length > 1) {
            sessionIdentifier = inArgs[1].trim();
            if (FIXClient.getFIXClient().reload(sessionIdentifier)) {
                output = sessionIdentifier + " reloaded.";
            }
        } else {
            List<String> reloadedSession = FIXClient.getFIXClient().reload();
            if (reloadedSession.isEmpty()) {
                output = "No sessions reloaded.";
            } else {
                output = "Reloaded Sessions: " + reloadedSession;
            }
        }
        return output;
    }

    private String readLine() {
        String message = "";
        try {
            message = this.reader.readLine();
        } catch (Exception e) {
            logger.error("Exception at Console Read" + e.getMessage(), e);
        }
        return message;
    }

    protected void closeConnection() {
        try {
            this.reader.close();
            this.writer.close();
            this.socket.close();
        } catch (Exception e) {
            logger.error("Exception at Console Client Connection Close: " + e.getMessage(), e);
        }
        this.socket = null;
        this.reader = null;
        this.writer = null;
        logger.debug("Console Client Disconnected - " + clientIp);
    }

    protected void closeConnectionWithMessage(String message){
        writeLine(message);
        closeConnection();
    }

    private String printDescription() {
        if (dfixDescription == null) {
            loadDescription();
        }
        return dfixDescription;
    }

    private void loadDescription() {
        StringBuilder stringBuilder;
        String str;
        try {
            stringBuilder = new StringBuilder();
            BufferedReader in;
            try (FileReader fileReader = new FileReader(IConstants.MANUAL_FILE)) {
                in = new BufferedReader(fileReader);
                str = in.readLine();
                while (str != null) {
                    stringBuilder.append(str).append("\n");
                    str = in.readLine();
                }
            }
            dfixDescription = stringBuilder.toString();
        } catch (Exception e) {
            logger.error("Error loading DFIXRouter.txt:" + e.getMessage(), e);  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
