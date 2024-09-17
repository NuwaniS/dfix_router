package com.mubasher.oms.dfixrouter.server.fix;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.InternalBean;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.logs.LogEventsEnum;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.admin.AdminManager;
import com.mubasher.oms.dfixrouter.server.admin.UIAdminServer;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.ExchangeMessageProcessorFactory;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.concurrent.MessageQHandler;
import com.mubasher.oms.dfixrouter.server.fix.flowcontrol.FlowControlStatus;
import com.mubasher.oms.dfixrouter.server.fix.flowcontrol.FlowController;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.WatchDogHandler;
import com.mubasher.oms.dfixrouter.util.stores.TradingMarketStore;
import com.mubasher.regional.utils.DataDisintegrator;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix42.ExecutionReport;
import quickfix.fix42.NewOrderSingle;
import quickfix.fix42.OrderCancelReplaceRequest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class FIXApplication extends FIXApplicationCommonLogic implements ApplicationExtended {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.FIXApplication");
    private String autoConnect;
    private HashMap<String, Long> orderLastExecutionTimeData = null;
    private HashMap<String, Integer> clOrdIdFixTag10008 = null;//message.getString(ClOrdID.FIELD), message.getInt(FixConstants.FIX_TAG_10008)
    private HashMap<String, Integer> accountFixTag10008 = null;//message.getString(Account.FIELD), message.getInt(FixConstants.FIX_TAG_10008)
    private Set<String> nonDisclosedTrdAccntSet;        //store all accountTag associated with accountType 2 (non disclosed)
    private Set<String> simOrderProcess = null;//message.getInt(Account.FIELD) or
    private HashMap<Integer, Integer> fixTag10008ServedBy = null;//message.getInt(FixConstants.FIX_TAG_10008), message.getInt(FixConstants.FIX_TAG_SERVED_BY)
    private HashMap<String, Integer> requestIdServedBy = null;//message.getInt(FixConstants.FIX_TAG_CLIENT_REQUEST_ID), message.getInt(FixConstants.FIX_TAG_SERVED_BY)
    private HashMap<String, Integer> sessionNoData = null;
    private HashMap<String, String> clOrdIdOmsReqIdMap = null; //message.getString(ClOrdID.FIELD), message.getInt(FixConstants.FIX_TAG_OMS_REQ_ID)
    private HashMap<String, String> accountFixTag10015 = null; //message.getString(Account.FIELD), message.getInt(FixConstants.FIX_TAG_CASH_ACNT_ID)
    private HashMap<String, String> seqNumResetDone;
    private HashMap<String, Integer> icmOrdIdQueueIdMap = null;
    private HashMap<String, Integer> icmRemoteOrdIdQueueIdMap = null;

    private boolean watchdogEnabled;
    private boolean groupingEnabled;
    private String dfixId;
    private int grpCount;
    private boolean isSimProcessICMOrders;

    public FIXApplication() {
        super();
        orderLastExecutionTimeData = new HashMap<>();
        clOrdIdFixTag10008 = new HashMap<>();
        accountFixTag10008 = new HashMap<>();
        fixTag10008ServedBy = new HashMap<>();
        requestIdServedBy = new HashMap<>();
        sessionNoData = new HashMap<>();
        simOrderProcess = new HashSet<>();
        clOrdIdOmsReqIdMap = new HashMap<>();
        accountFixTag10015 = new HashMap<>();
        icmOrdIdQueueIdMap = new HashMap<>();
        icmRemoteOrdIdQueueIdMap = new HashMap<>();
        watchdogEnabled = (IConstants.SETTING_YES).equalsIgnoreCase(Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG));
        dfixId = SettingsConstants.CLUSTER_MEMBER_PREFIX + "-" + Settings.getProperty(IConstants.SETTING_DFIX_ID);
        groupingEnabled = IConstants.SETTING_YES.equals(Settings.getProperty(SettingsConstants.EXCHANGE_LEVEL_GROUPING));
        if (groupingEnabled) {
            grpCount = Settings.getInt(SettingsConstants.JMS_SESSION_COUNT);
        }
        autoConnect = Settings.getProperty(SettingsConstants.TRADING_SESSIONS_AUTO_CONNECT);
        seqNumResetDone = new HashMap<>();
        isSimProcessICMOrders = (IConstants.SETTING_YES).equalsIgnoreCase(Settings.getProperty(SettingsConstants.SIM_PROCESS_ICM_ORDERS));
        nonDisclosedTrdAccntSet = new HashSet<>();
    }

    /**
     * Callback function. This get called when FIX Client session is
     * created.
     */
    @Override
    public void onCreate(SessionID sessionID) {
        logger.debug("onCreate Session :" + getSessionIdentifier(sessionID));
        populateSessionNoData(sessionID);
    }

    /**
     * Callback function. This get called when FIX client get successfully
     * logged in to the FIX gateway
     */
    @Override
    public void onLogon(SessionID sessionID) {
        DFIXMessage msg = new DFIXMessage();
        msg.setExchange(getSessionIdentifier(sessionID));
        msg.setSequence(getExecutionId());
        msg.setMessage("LOGGED_INTO_EXCHANGE");
        msg.setMessage(addCustomFieldsToOMS(FIXClient.getSettings().get(sessionID), msg.getMessage(), sessionID));
        msg.setType(Integer.toString(IConstants.SESSION_CONNECTED));
        boolean isSend = handleLoggingEvent(IConstants.SESSION_CONNECTED, sessionID);
        if (isSend && DFIXRouterManager.getFromExchangeQueue() != null) {
            DFIXRouterManager.getFromExchangeQueue().addMsg(msg);
        }
        sendAdminMessage(IConstants.ALL_SESSIONS, AdminManager.getInstance().sendSessionList());
        sendLinkStatusConnected(dfixId, sessionID);
        logger.debug("Logged into Session :" + getSessionIdentifier(sessionID));
    }

    /*
     * Callback function. This get called when FIX client get logeded out
     * from the FIX gateway
     */
    @Override
    public void onLogout(SessionID sessionID) {
        DFIXMessage msg = new DFIXMessage();
        msg.setExchange(getSessionIdentifier(sessionID));
        msg.setSequence(getExecutionId());
        msg.setMessage("LOGGED_OUT_FROM_EXCHANGE");
        msg.setMessage(addCustomFieldsToOMS(FIXClient.getSettings().get(sessionID), msg.getMessage(), sessionID));
        msg.setType(Integer.toString(IConstants.SESSION_DISCONNECTED));
        boolean isSend = handleLoggingEvent(IConstants.SESSION_DISCONNECTED, sessionID);
        if (isSend && DFIXRouterManager.getFromExchangeQueue() != null) {
            DFIXRouterManager.getFromExchangeQueue().addMsg(msg);
        }
        sendAdminMessage(msg.getExchange(), IConstants.STRING_SESSION_DISCONNECTED);
        sendLinkStatusDisconnected(dfixId, sessionID);
        logger.debug("Logged out from Session :" + getSessionIdentifier(sessionID));
    }

    /*
     * Callback function. This get called when FIX client is about to send a
     * administrative message to the FIX gateway. Administrate message can
     * be modified here. eg. Sequence reset.
     */
    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        final Message.Header header = message.getHeader();
        try {
            if (header.getField(new MsgType()).valueEquals(MsgType.LOGON)) {
                addLogonField(message, sessionID);
                sendAdminMessage(getSessionIdentifier(sessionID), IConstants.SESSION_SENT_LOGON);
                if (IConstants.SETTING_YES.equalsIgnoreCase(FIXClient.getFIXClient().getSessionProperty(sessionID, IConstants.SETTING_TAG_IS_SCH_PASS_RESET))){
                    String sessionIdentifier = getSessionIdentifier(sessionID);
                    logger.info("Reset Password is enabled for session: " + sessionIdentifier);
                    File file = new File("./quick_fix/output/" + sessionIdentifier + ".txt");
                    SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd");
                    String userName = FIXClient.getFIXClient().getSessionProperty(sessionID, IConstants.SETTING_TAG_USER_NAME) != null
                            ? FIXClient.getFIXClient().getSessionProperty(sessionID, IConstants.SETTING_TAG_USER_NAME) : sessionID.getSenderCompID();
                    message.setString(Username.FIELD, userName);
                    updateSessionPassword(message, file, sessionIdentifier, sdf);
                }
            }
        } catch (Exception e) {
            logger.error("Send Admin Msg to FIX Session Failed: " + e.getMessage(), e);
        }
    }

    private void updateSessionPassword(Message message, File file, String sessionIdentifier, SimpleDateFormat sdf) throws IOException {
        String currPass;
        if (file.exists()){
            logger.info("Reading password file for session: " + sessionIdentifier);
            try {
                FileReader fileReader = new FileReader(file);
                BufferedReader in = new BufferedReader(fileReader);
                currPass = in.readLine();
                fileReader.close(); // need to close fileReader before calling replacePassword
                Date expDate = sdf.parse(currPass.substring(3, 9));
                message.setString(Password.FIELD, currPass);
                if (!expDate.after(new Date())){
                    currPass = getNewPassword(sdf, sessionIdentifier);
                    message.setString(NewPassword.FIELD, currPass);
                    replacePassword(file, currPass, sessionIdentifier);
                }
            } catch (Exception e) {
                logger.error("Update FIX Session Password Failed: " + e.getMessage(), e);  //To change body of catch statement use File | Settings | File Templates.
            }
        } else {
            logger.warn("Password file is not available for session: " + sessionIdentifier);
            currPass = getNewPassword(sdf, sessionIdentifier);
            message.setString(Password.FIELD, currPass);
            message.setString(NewPassword.FIELD, currPass);
            replacePassword(file, currPass, sessionIdentifier);
        }
    }

    private void replacePassword(File file, String newPass, String sessionIdentifier) throws IOException {
        logger.warn("Replacing the password for session: " + sessionIdentifier);
        Files.deleteIfExists(file.toPath());
        if(!Files.exists(file.getParentFile().toPath(), LinkOption.NOFOLLOW_LINKS)){
            //if relative parent folder does not exist , recursively create folder structure
            Files.createDirectories(file.getParentFile().toPath());
        }
        try (FileWriter fileWriter = new FileWriter(file)){
            fileWriter.write(newPass);
        } catch (Exception e) {
            logger.error("Replace FIX Session Password Failed: " + e.getMessage(), e);  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private String getNewPassword(SimpleDateFormat sdf, String sessionIdentifier) {
        logger.warn("Creating the password for session: " + sessionIdentifier);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MONTH, 1);
        calendar.set(Calendar.DATE, 1);
        StringBuilder stringBuilder = new StringBuilder("Yu@");
        stringBuilder.append(sdf.format(calendar.getTime())).append("!");
        return stringBuilder.toString();
    }

    /*
     * Callback function. This get called when FIX client receives a
     * administrative message from the FIX gateway eg. Sequence reset.
     */
    @Override
    public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        String sessionIdentifier = getSessionIdentifier(sessionID);
        verifyLoginStateForAdminMessage(sessionID, sessionIdentifier);
        final Message.Header header = message.getHeader();
        if (!header.getField(new MsgType()).valueEquals(MsgType.HEARTBEAT)) {
            StringBuilder sb = new StringBuilder("From FIX Gateway :").append(message);
            logger.info(sb.toString());
        }
        if (header.getField(new MsgType()).valueEquals(MsgType.REJECT)) {
            handleAdminMsgReject(message, sessionID, sessionIdentifier);
        } else if (header.getField(new MsgType()).valueEquals(MsgType.LOGOUT)
                && message.isSetField(Text.FIELD)){
            handleAdminMsgLogout(message, sessionID);
        }
        if (IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.IS_PROCESS_EXCHANGE_MSG)) && message.isSetField(new ResetSeqNumFlag())) {
            ExchangeMessageProcessorFactory.regenLogOnMsg(message, sessionID, getSessionIdentifier(sessionID));
        }
    }

    private void handleAdminMsgLogout(Message message, SessionID sessionID) throws FieldNotFound {
        String expectedSeqNumber = null;
        String rejectReason = message.getString(Text.FIELD);
        if (rejectReason.contains("Expected/Received =")) {
            //Lower sequence received than expected without PossDup flag. Expected/Received = 1120/1
            expectedSeqNumber = message.getString(Text.FIELD).split("=")[1].split("/")[0].trim();
        } else if (rejectReason.contains("expecting") && rejectReason.contains("but received")) {
            //MsgSeqNum too low, expecting 171 but received 13
            expectedSeqNumber = rejectReason.substring(rejectReason.indexOf("expecting") + 10, rejectReason.indexOf("but received") - 1);
        } else if (rejectReason.contains("expected sequence number:")){
            //TEST_3386- CODE: 1-208-0-34: Serious Error: Message sequence number: 5 is less than expected sequence number: 5500
            //TEST_3386- CODE: 1-208-A-34: Serious Error: Message sequence number: 12916 is less than expected sequence number: 14138
            expectedSeqNumber = rejectReason.substring(rejectReason.indexOf("expected sequence number:") + 26);
        }
        if (expectedSeqNumber != null) {
            logger.info("Reset Out Sequence number to : " + expectedSeqNumber);
            Message resetMessage = new Message();
            resetMessage.getHeader().setString(MsgType.FIELD, "DI");
            resetMessage.setInt(ListID.FIELD, FixConstants.FIX_TAG_RESET_OUT_SEQ_DETAIL);
            Group group = new Group(NoOrders.FIELD, 10008);
            group.setString(10008, expectedSeqNumber);
            resetMessage.addGroup(group);
            handleDFIXInformationMessages(resetMessage, sessionID);
        } else {
            logger.warn("Logout Rejection is not in expected Format: " + rejectReason);
        }
    }

    private void handleAdminMsgReject(Message message, SessionID sessionID, String sessionIdentifier) {
        boolean isSendToOms = true;
        processRejectionMessages(message, sessionID);
        try {
            Properties prop = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true);
            if ((message.getHeader().isSetField(DeliverToCompID.FIELD) || message.getHeader().isSetField(DeliverToSubID.FIELD))) {
                isSendToOms = false;
                addCustomFieldsToOMS(FIXClient.getSettings().get(sessionID), message, sessionID);
                handleReRouteTags(prop, message, DeliverToCompID.FIELD, DeliverToSubID.FIELD);
            }
        } catch (Exception e) {
            logger.error("35=3 Processing Failed: " + e.getMessage(), e);
        }
        if (isSendToOms && DFIXRouterManager.getFromExchangeQueue() != null){
            //send 35=3 messages to OMS
            DFIXMessage dfixMsg = new DFIXMessage();
            dfixMsg.setExchange(sessionIdentifier);
            dfixMsg.setSequence(getExecutionId());
            addCustomFieldsToOMS(FIXClient.getSettings().get(sessionID), message, sessionID);
            dfixMsg.setMessage(message.toString());
            dfixMsg.setType(Integer.toString(IConstants.APPLICATION_MESSAGE_RECEIVED));
            DFIXRouterManager.getFromExchangeQueue().addMsg(dfixMsg);
        }
    }

    private void verifyLoginStateForAdminMessage(SessionID sessionID, String sessionIdentifier) throws RejectLogon {
        if (FIXClient.getFIXClient().getManuallyLoggedOutAcceptors().contains(sessionID)) {
            throw new RejectLogon("Manually Logged Out session: " + sessionIdentifier);
        }
    }

    /*
     * Callback function. This get called when FIX client is about to send
     * an order message to the FIX gateway. Order can be modified here eg.
     * new Order.
     */
    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        StringBuilder sb = new StringBuilder("To FIX Gateway =").append(message);
        logger.info(sb.toString());
        try {
            storeIntermediateQueueData(message, true);
            storeHAServerData(message);
            processTagModifications(message, sessionID, false);
            changeTargetCompID(message, sessionID);
            super.addCustomFields(FIXClient.getSettings().get(sessionID), message, sessionID);
            addRepeatingGroup(message, sessionID);
            boolean isFromOms = message.isSetField(FixConstants.FIX_TAG_IS_FROM_OMS);
            if (isFromOms) {
                message.removeField(FixConstants.FIX_TAG_IS_FROM_OMS);
            }
            populateOMSReqIdMap(message);
            clearUnwantedData(message);
            boolean isSend = checkFlowControl(message, sessionID, !isFromOms);
            if (!isSend) {
                logger.error("Flow Control rejects the message: " + message.toString());
                throw new DoNotSend();
            }
        } catch (Exception e) {
            logger.error("Failed Msg Sending To FIX Gateway: " + e.getMessage(), e);
            throw new DoNotSend();
        }
    }

    /*
     * Callback function. This get called when FIX client receives a
     * execution/rejection message from the FIX gateway
     */
    @Override
    public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        StringBuilder sb = new StringBuilder("From FIX Gateway =").append(message);
        logger.info(sb.toString());
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            if (!FixConstants.FIX_VALUE_35_DFIX_INFORMATION.equals(msgType)) {
                handleNonDIMessages(message, sessionID);
            } else {
                handleDFIXInformationMessages(message, sessionID);
            }
        } catch (Exception e) {
            logger.error("Failed Msg Processing From FIX Gateway: " + e.getMessage(), e);
        }
    }

    private void handleNonDIMessages(Message message, SessionID sessionID) throws ConfigError, FieldNotFound {
        String sessionIdentifier = getSessionIdentifier(sessionID);
        String msgType = message.getHeader().getString(MsgType.FIELD);
        boolean isSend;

        processTagModifications(message, sessionID, true);
        DFIXMessage dfixMsg = new DFIXMessage();
        dfixMsg.setExchange(sessionIdentifier);
        dfixMsg.setSequence(getExecutionId());
        dfixMsg.setType(Integer.toString(IConstants.APPLICATION_MESSAGE_RECEIVED));
        dfixMsg.setFixMsgType(msgType);
        processRejectionMessages(message, sessionID);
        setOMSReqId(message);
        if (ExchangeMessageProcessorFactory.isIgnoreDropCopyMessage(message, dfixMsg.getExchange(), sessionID)) {
            return;
        }
        populateICMDropCopyQueueData(message, sessionID);
        populateIntermediateQueueData(message, dfixMsg);
        addCustomFieldsToOMS(FIXClient.getSettings().get(sessionID), message, sessionID);
        dfixMsg.setMessage(message.toString());
        populateHAData(message, dfixMsg);
        setMsgGroupId(dfixMsg, message);
        clearUnwantedData(message);
        isSend = processExchangeMessage(message, sessionID, dfixMsg);
        if (isSend) {
            isSend = checkFlowControl(message, sessionID, true);
        }
        if (isSend && IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.IS_CLUBBING_ENABLED))) {
            resolveClubbingSettings(message);
            isSend = isNotClubbingExecution(message, sessionID);
        }
        if (isSend) {
            Properties prop = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true);
            if (prop.containsKey(IConstants.SETTING_TAG_SEND_TO_OMS)
                    && prop.get(IConstants.SETTING_TAG_SEND_TO_OMS).toString().equalsIgnoreCase(IConstants.SETTING_NO)) {
                isSend = handleNonOMSMessages(message, prop);
            } else if ((message.getHeader().isSetField(DeliverToCompID.FIELD) || message.getHeader().isSetField(DeliverToSubID.FIELD))) {
                isSend = handleReRouteTags(prop, message, DeliverToCompID.FIELD, DeliverToSubID.FIELD);
            }
            if (isSend && DFIXRouterManager.getFromExchangeQueue() != null) {
                DFIXRouterManager.getFromExchangeQueue().addMsg(dfixMsg);
            }
        }
    }

    private static boolean processExchangeMessage(Message message, SessionID sessionID, DFIXMessage dfixMsg) {
        boolean isSend = true;
        if (IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.IS_PROCESS_EXCHANGE_MSG))) {
            if (TradingMarketStore.isEnquiry() && dfixMsg.getFixMsgType().equals(MsgType.EXECUTION_REPORT)) {
                isSend = false;
                InternalBean internalBean = new InternalBean(message, sessionID);
                try {
                    MessageQHandler messageQHandler = MessageQHandler.getSharedInstance();
                    messageQHandler.putMessageToQueue(internalBean);
                } catch (Exception e1) {
                    logger.info(" failed to add to internal Q");
                }
            } else {
                isSend = ExchangeMessageProcessorFactory.processMessage(dfixMsg.getExchange(), message.toString());       //decision whether to send the msg or not
            }
        }
        return isSend;
    }

    private void handleDFIXInformationMessages(Message message, SessionID sesID) {
        String sessionIdentifier = getSessionIdentifier(sesID);

        logger.info("DFIX has received DI message: " + message.toString());
        try {
            if (message.getHeader().getString(MsgType.FIELD).equals(FixConstants.FIX_VALUE_35_DFIX_INFORMATION)) {
                Properties prop = FIXClient.getSettings().get(sesID).getSessionProperties(sesID, true);
                Group group = null;
                if (message.hasGroup(NoOrders.FIELD)) {
                    group = message.getGroups(NoOrders.FIELD).get(0);
                }
                if (message.isSetField(ListID.FIELD)) {
                    switch (message.getInt(ListID.FIELD)){
                        case FixConstants.FIX_TAG_RESET_IN_SEQ_DETAIL :
                            processDIMsgResetInSeqDetail(sessionIdentifier, prop, group);
                            break;
                        case FixConstants.FIX_TAG_RESET_OUT_SEQ_DETAIL :
                            processDIMsgResetOutSeqDetail(sessionIdentifier, prop, group);
                            break;
                        case IConstants.SESSION_STATUS:
                            processDIMsgSessionStatus(message, sessionIdentifier);
                            break;
                        default:
                            logger.error("DI message field value not recognized for field ListID (66)  " + message.getInt(ListID.FIELD));
                            break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("DI Msg Processing Failed: " + e.getMessage(), e);
        }
    }

    private void processDIMsgSessionStatus(Message message, String sessionIdentifier) throws FieldNotFound, InvalidMessage, SessionNotFound {
        if ((message.getHeader().isSetField(DeliverToCompID.FIELD) || message.getHeader().isSetField(DeliverToSubID.FIELD))) {
            String onBehalfId = (message.getHeader().isSetField(DeliverToCompID.FIELD)
                    ? message.getHeader().getString(DeliverToCompID.FIELD) : message.getHeader().getString(DeliverToSubID.FIELD));
            SessionID onBehalfOfSessionID = FIXClient.getFIXClient().getSessionID(onBehalfId);
            String targetSessionStatusString = FIXClient.getFIXClient().getSessionStatus(onBehalfOfSessionID);
            int targetSessionStatus = IConstants.SESSION_CONNECTED;
            if (!IConstants.STRING_SESSION_CONNECTED.equalsIgnoreCase(targetSessionStatusString)){
                targetSessionStatus = IConstants.SESSION_DISCONNECTED;
            }
            FIXClient.getFIXClient().sendString(buildSessionStatusResponse(targetSessionStatus), sessionIdentifier);
        }
    }

    private void processDIMsgResetOutSeqDetail(String sessionIdentifier, Properties prop, Group group) throws FieldNotFound {
        if (prop.containsKey(IConstants.SETTING_TAG_INSTANT_AUTO_SYNC_SEQ_NO)
                && IConstants.SETTING_YES.equalsIgnoreCase(prop.get(IConstants.SETTING_TAG_INSTANT_AUTO_SYNC_SEQ_NO).toString())) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HH:mm");
            if (seqNumResetDone.get(sessionIdentifier) == null || !sdf.format(new Date()).equals(seqNumResetDone.get(sessionIdentifier))) {
                seqNumResetDone.put(sessionIdentifier, sdf.format(new Date()));
                if (group != null) {
                    AdminManager.getInstance().resetOutSequence(sessionIdentifier, group.getInt(FixConstants.FIX_TAG_10008));
                } else {
                    logger.warn("DI - RESET_OUT_SEQ_DETAIL Message without details.");
                }
            } else {
                logger.warn("Ignoring Out Sequence Reset message since it is already Reset once for the minute.");
            }
        } else {
            logger.warn("Out Sequence Reset message received but, InstantAutoSyncSeqNo Settings in fix.cfg file is not allowing to do so.");
        }
    }

    private static void processDIMsgResetInSeqDetail(String sessionIdentifier, Properties prop, Group group) throws FieldNotFound {
        if (prop.containsKey(IConstants.SETTING_TAG_INSTANT_AUTO_SYNC_SEQ_NO)
                && IConstants.SETTING_YES.equalsIgnoreCase(prop.get(IConstants.SETTING_TAG_INSTANT_AUTO_SYNC_SEQ_NO).toString())) {
            if (group != null) {
                AdminManager.getInstance().resetInSequence(sessionIdentifier, group.getInt(FixConstants.FIX_TAG_10008));
            } else {
                logger.warn("DI - RESET_IN_SEQ_DETAIL Message without details.");
            }
        } else {
            logger.warn("In Sequence Reset message received but, InstantAutoSyncSeqNo Settings in fix.cfg file is not allowing to do so.");
        }
    }

    private boolean handleNonOMSMessages(Message message, Properties prop) throws FieldNotFound {
        int primary= -1;
        int secondary = -1;
        if (message.getHeader().isSetField(OnBehalfOfCompID.FIELD) || message.getHeader().isSetField(OnBehalfOfSubID.FIELD)) {
            primary = OnBehalfOfCompID.FIELD;
            secondary = OnBehalfOfSubID.FIELD;
        } else if ((message.getHeader().isSetField(DeliverToCompID.FIELD) || message.getHeader().isSetField(DeliverToSubID.FIELD))){
            primary = DeliverToCompID.FIELD;
            secondary = DeliverToSubID.FIELD;
        }
        if (primary != -1 && secondary != -1) {
            return handleReRouteTags(prop, message, primary, secondary);
        } else {
            return true;
        }
    }

    private boolean handleReRouteTags(Properties prop, Message message, int primary, int secondary) throws FieldNotFound {
        //reRoutingField,primary,secondary > 0 ( validation handled in the called method)
        // at least one of primary or secondary field available in the Header
        boolean isSendToOMS = false;
        int reRoutingField = secondary; //default consider the secondary field
        if (message.getHeader().isSetField(primary)){
            reRoutingField = primary;
        }
        String onBehalfId = message.getHeader().getString(reRoutingField);
        SessionID onBehalfOfSessionID = FIXClient.getFIXClient().getSessionID(onBehalfId);
        if (onBehalfOfSessionID != null) {
            logger.info("Re-Routing the messages to Session: " + onBehalfId);
            try {
                Properties targetProp = FIXClient.getSettings().get(onBehalfOfSessionID).getSessionProperties(onBehalfOfSessionID, true);
                if (targetProp.containsKey(IConstants.SETTING_USER_DEFINED_HEADER_TO_OMS_TAGS)
                        && targetProp.getProperty(IConstants.SETTING_USER_DEFINED_HEADER_TO_OMS_TAGS).contains(String.valueOf(reRoutingField))) {
                    logger.debug("Configuration handled Re-Routing");
                    message.getHeader().removeField(reRoutingField);
                } else {
                    message.getHeader().setString(reRoutingField, prop.getProperty(IConstants.SESSION_IDENTIFIER));
                }
            } catch (ConfigError configError) {
                logger.error("Error in Configuration handling Re-Routing", configError);
            }
            sendToTarget(message, onBehalfOfSessionID);
        } else {
            logger.warn("Sending the Message to OMS since Re-Routing tag has invalid session identifier - " + onBehalfId);
            isSendToOMS = true;
        }
        return isSendToOMS;
    }

    public void setOMSReqId(Message message) {
        try {
            if (message.isSetField(ClOrdID.FIELD)) {
                String omsReqId = clOrdIdOmsReqIdMap.get(message.getString(ClOrdID.FIELD));
                if (omsReqId != null && !omsReqId.isEmpty()) {
                    logger.elklog(omsReqId, LogEventsEnum.RECEIVED_FRM_EXCHANGE, "Received from Exchange:" + message.toString());
                    message.setField(new StringField(FixConstants.FIX_TAG_OMS_REQ_ID, omsReqId));
                }
            }
        } catch (Exception e) {
            logger.error("OMS Request Id resolve failed:" + e.getMessage());
        }
    }

    /*Msg grouping is done for the exchange account number. Generate one group for the messages of one exchange account*/
    protected void setMsgGroupId(DFIXMessage dfixMessage, Message message) {
        if ((groupingEnabled)) {
            try {
                String account = getAccToPopulateInterQueueData(message);
                // getAccToPopulateInterQueueData is refactored removing  additional validation on PartyIDSource
                //group.isSetField(PartyIDSource.FIELD) &&
                // (group.getChar(PartyIDSource.FIELD) == PartyIDSource.CSD_PARTICIPANT_MEMBER_CODE || group.getChar(PartyIDSource.FIELD) == PartyIDSource.GENERALLY_ACCEPTED_MARKET_PARTICIPANT_IDENTIFIER))
                if (account != null) {
                    String groupId = account;
                    //generateID method is used to convert the exchange account number to an int without failure,
                    //The same logic is used at the OMS side to generate the msg grp id when it is not sent by the DFIX
                    if (accountFixTag10015.containsKey(account)) {
                        groupId = accountFixTag10015.get(account);
                    }
                    //0 is the default group and it will be resolved for admin and market status messages
                    int msgGrpId = (generateID(groupId) % grpCount) + 1;
                    dfixMessage.setMsgGroupId(String.valueOf(msgGrpId));
                }
            } catch (Exception e) {
                //doNothing - default group is set
            }
        }
    }

    private void resolveClubbingSettings(Message message) throws FieldNotFound {
        boolean isAddToClub = false;
        if (message instanceof ExecutionReport) {
            isAddToClub = checkOrderStatus(message);
            if (!isAddToClub) {
                isAddToClub = checkFilledQuantity(message);
            }
            if (!isAddToClub) {
                isAddToClub = checkExecutionTimeDiff(message);
            }
        }
        if (isAddToClub) {
            DFIXRouterManager.getInstance().getExchangeExecutionMerger().getClOrdIdList().add(message.getString(ClOrdID.FIELD));
        }
    }

    public boolean isNotClubbingExecution(Message message, SessionID sessionID) {      //returns false if clubbing execution
        boolean isNotClubbingExecution = true;
        try {
            if (message != null
                    && sessionID != null
                    && message.isSetField(ClOrdID.FIELD)
                    && DFIXRouterManager.getInstance().getExchangeExecutionMerger().getClOrdIdList().contains(message.getString(ClOrdID.FIELD))) {
                DFIXRouterManager.getInstance().getExchangeExecutionMerger().addMsg(message, getSessionIdentifier(sessionID));
                logger.debug("Execution report will be clubbed:" + message.toString());
                isNotClubbingExecution = false;
            }
        } catch (Exception ex) {
            logger.error("Error in processing execution report for clubbing :", ex);
        }
        return isNotClubbingExecution;
    }

    private boolean checkOrderStatus(Message message) throws FieldNotFound {
        boolean isAddToClub = false;
        long now = System.currentTimeMillis();
        if (message.getChar(OrdStatus.FIELD) == OrdStatus.NEW
                || message.getChar(OrdStatus.FIELD) == OrdStatus.FILLED
                || message.getChar(OrdStatus.FIELD) == OrdStatus.REPLACED
                || message.getChar(OrdStatus.FIELD) == OrdStatus.CANCELED) {
            isAddToClub = true;
            if (message.getChar(OrdStatus.FIELD) == OrdStatus.FILLED) {
                orderLastExecutionTimeData.remove(message.getString(ClOrdID.FIELD));
            }
            if (message.isSetField(OrigClOrdID.FIELD)
                    && orderLastExecutionTimeData.containsKey(message.getString(OrigClOrdID.FIELD))) {
                orderLastExecutionTimeData.remove(message.getString(OrigClOrdID.FIELD));
            }
            if (message.getChar(OrdStatus.FIELD) != OrdStatus.FILLED) {
                orderLastExecutionTimeData.put(message.getString(ClOrdID.FIELD), now);
            }
        }
        if (isAddToClub) {
            logger.debug("Clubbing because of OrderStatus: " + message.getChar(OrdStatus.FIELD));
        }
        return isAddToClub;
    }

    private boolean checkFilledQuantity(Message message) throws FieldNotFound {
        boolean isAddToClub = false;
        if (message.isSetField(OrderQty.FIELD) && message.isSetField(LastShares.FIELD)) {
            int orderQty = message.getInt(OrderQty.FIELD);
            int lastShares = message.getInt(LastShares.FIELD);
            double filledRatio = (double) lastShares / orderQty;
            if (Settings.getClubbingRatio() > 0) {
                isAddToClub = (filledRatio <= Settings.getClubbingRatio());
            }
            if (isAddToClub) {
                logger.debug("Clubbing because of Low FilledQuantity: " + lastShares + " OrderQuantity: " + orderQty + " ClubbingRatio: " + Settings.getClubbingRatio());
            }
        }
        return isAddToClub;
    }

    private boolean checkExecutionTimeDiff(Message message) throws FieldNotFound {
        boolean isAddToClub = false;
        long now = System.currentTimeMillis();
        long diff = 0;
        if (orderLastExecutionTimeData.containsKey(message.getString(ClOrdID.FIELD))) {
            diff = now - orderLastExecutionTimeData.get(message.getString(ClOrdID.FIELD));
        }
        orderLastExecutionTimeData.put(message.getString(ClOrdID.FIELD), now);
        if (diff > 0) {
            isAddToClub = (diff <= Settings.getClubbingTimeOut());
            if (isAddToClub) {
                logger.debug("Clubbing because of FastExecution. Time different from Last Execution: " + diff);
            }
        }
        return isAddToClub;
    }

    private void storeIntermediateQueueData(Message message, boolean isRemoveTag) {
        String account = null;
        try {
            account = updateFixTagMaps(message, isRemoveTag, account);

            if (message.isSetField(FixConstants.FIX_TAG_10014)) {
                message.removeField(FixConstants.FIX_TAG_10014);
            }
            simProcessICMOrdersToStoreIntermediateQueueData(message);
            if (message.isSetField(FixConstants.FIX_TAG_INTER_QUEUE_ID)) {
                message.removeField(FixConstants.FIX_TAG_INTER_QUEUE_ID);
            }

            updateSimOrderProcessMap(message, account);

            if (message.isSetField(FixConstants.FIX_TAG_CASH_ACNT_ID)) {
                message.removeField(FixConstants.FIX_TAG_CASH_ACNT_ID);
            }
            if (message.isSetField(FixConstants.FIX_TAG_10019_TRADING_ACC_TYPE)) {
                message.removeField(FixConstants.FIX_TAG_10019_TRADING_ACC_TYPE);
            }
        } catch (FieldNotFound fnf) {
            logger.error("Error at storing intermediate Queue Data: " + fnf.getMessage(), fnf);
        }
    }

    private String updateFixTagMaps(Message message, boolean isRemoveTag, String account) throws FieldNotFound {
        int fixTag10008 = -1;
        int fixTag10019 = -1 ;
        if (message.isSetField(FixConstants.FIX_TAG_10019_TRADING_ACC_TYPE)) {
            fixTag10019 = message.getInt(FixConstants.FIX_TAG_10019_TRADING_ACC_TYPE);
        }
        if (message.isSetField(FixConstants.FIX_TAG_10008)) {
            fixTag10008 = message.getInt(FixConstants.FIX_TAG_10008);
            storeTag10008AgainstCliord(message, fixTag10008);
            storeTag10008AgainstOrigCliord(message, fixTag10008);
            account = getAccToPopulateInterQueueData(message);
            account = formatAccountString(account);
            if (account != null) {
                processTag10008ForOmnibusAccounts(fixTag10019, account);
                if (!accountFixTag10008.keySet().contains(account) && fixTag10019 != IConstants.CONSTANT_TWO_2 && !nonDisclosedTrdAccntSet.contains(account)) {
                    logger.debug("fixTag10008 Stored against account: " + fixTag10008 + " - " + account);
                    accountFixTag10008.put(account, fixTag10008);
                }
                if (!accountFixTag10015.keySet().contains(account) && message.isSetField(FixConstants.FIX_TAG_CASH_ACNT_ID)) {
                    String fixTag10015  = message.getString(FixConstants.FIX_TAG_CASH_ACNT_ID);
                    logger.debug("fixTag10015 Stored against account: " + fixTag10015 + " - " + account);
                    accountFixTag10015.put(account, fixTag10015);
                }
            }
            account = storeTag10018AgainstTag10014(message, fixTag10008, account);
            if (isRemoveTag) {
                message.removeField(FixConstants.FIX_TAG_10008);
            }
        } else if (message.isSetField(ClOrdID.FIELD) && message.isSetField(OrigClOrdID.FIELD)) {
            String origClOrdId = message.getString(OrigClOrdID.FIELD);
            if (clOrdIdFixTag10008.keySet().contains(origClOrdId)) {
                fixTag10008 = clOrdIdFixTag10008.get(origClOrdId);
            }
            String clOrdId = message.getString(ClOrdID.FIELD);
            if (fixTag10008 != -1 && !clOrdIdFixTag10008.keySet().contains(clOrdId)) {
                logger.debug("fixTag10008 Stored against clOrdid: " + fixTag10008 + " - " + clOrdId);
                clOrdIdFixTag10008.put(clOrdId, fixTag10008);
            }
        }
        return account;
    }

    private void updateSimOrderProcessMap(Message message, String account) throws FieldNotFound {
        if (message.isSetField(FixConstants.FIX_TAG_SIM_PROCESS)) {
            String isSimProcess = message.getString(FixConstants.FIX_TAG_SIM_PROCESS);
            if (!simOrderProcess.contains(isSimProcess)) {
                logger.debug("Account Numbers Stored for Simultaneous Processing " + isSimProcess + " & " + account);
                simOrderProcess.add(isSimProcess);
                if (account != null) {
                    simOrderProcess.add(account);
                }
            }
            message.removeField(FixConstants.FIX_TAG_SIM_PROCESS);
        }
    }

    private void simProcessICMOrdersToStoreIntermediateQueueData(Message message) throws FieldNotFound {
        if (isSimProcessICMOrders() && (message.isSetField(FixConstants.FIX_TAG_SIM_PROCESS_ICM_REMOTE_ORD))) {
            String remoteClOrdId = message.getString(FixConstants.FIX_TAG_SIM_PROCESS_ICM_REMOTE_ORD);
            String orderIdICM = message.getString(ClOrdID.FIELD);
            int innerQueueId = message.getInt(FixConstants.FIX_TAG_INTER_QUEUE_ID);
            if (message.isSetField(OrigClOrdID.FIELD) && icmOrdIdQueueIdMap.keySet().contains(message.getString(OrigClOrdID.FIELD))) {
                innerQueueId = icmOrdIdQueueIdMap.get(message.getString(OrigClOrdID.FIELD));
            }
            if (!icmOrdIdQueueIdMap.keySet().contains(orderIdICM)) {
                logger.debug("Adding to icmOrdIdQueueIdMap :" + orderIdICM + ", Queue" + innerQueueId);
                icmOrdIdQueueIdMap.put(orderIdICM, innerQueueId);
            }
            if (!icmRemoteOrdIdQueueIdMap.keySet().contains(remoteClOrdId)) {//Route 35=G 35=F to same ORD queue
                logger.debug("Adding to icmRemoteOrdIdQueueIdMap :" + remoteClOrdId + ", Queue" + innerQueueId);
                icmRemoteOrdIdQueueIdMap.put(remoteClOrdId, innerQueueId);
            }
            message.removeField(FixConstants.FIX_TAG_SIM_PROCESS_ICM_REMOTE_ORD);

        }
    }

    private void processTag10008ForOmnibusAccounts(int fixTag10019, String account) {
        if (fixTag10019 == IConstants.CONSTANT_TWO_2) {
            nonDisclosedTrdAccntSet.add(account);
            //when another customer use same parent account to make a non-disclosure type order ,
            //original parent account mapping is removed to avoid collision
            if (accountFixTag10008.keySet().contains(account)) {
                logger.debug("fixTag10008 removed against account: " + accountFixTag10008.get(account) + " - " + account);
                accountFixTag10008.remove(account);
            }
        }
    }

    private String storeTag10018AgainstTag10014(Message message, int fixTag10008, String account) throws FieldNotFound {
        if (message.isSetField(FixConstants.FIX_TAG_10014)){
            String fixTag10014 = message.getString(FixConstants.FIX_TAG_10014);
            if (!accountFixTag10008.keySet().contains(fixTag10014)){
                logger.debug("fixTag10008 Stored against fixTag10014: " + fixTag10008 + " - " + fixTag10014);
                accountFixTag10008.put(fixTag10014, fixTag10008);
            }
            account = fixTag10014;
        }
        return account;
    }

    private void storeTag10008AgainstCliord(Message message, int fixTag10008) throws FieldNotFound {
        if (message.isSetField(ClOrdID.FIELD)) {
            String clOrdId = message.getString(ClOrdID.FIELD);
            if (!clOrdIdFixTag10008.keySet().contains(clOrdId)) {
                logger.debug("fixTag10008 Stored against clOrdid: " + fixTag10008 + " - " + clOrdId);
                clOrdIdFixTag10008.put(clOrdId, fixTag10008);
            }
        }
    }

    private void storeTag10008AgainstOrigCliord(Message message, int fixTag10008) throws FieldNotFound {
        if (message.isSetField(OrigClOrdID.FIELD)) {
            String origClOrdId = message.getString(OrigClOrdID.FIELD);
            if (!clOrdIdFixTag10008.keySet().contains(origClOrdId)) {
                logger.debug("fixTag10008 Stored against origClOrdID: " + fixTag10008 + " - " + origClOrdId);
                clOrdIdFixTag10008.put(origClOrdId, fixTag10008);
            }
        }
    }

    private static String formatAccountString(String account) {
        try {
            account = Integer.toString(Integer.parseInt(account));
        } catch (NumberFormatException nfe){
            //Do Nothing for AlphaNumeric value for account.
        }
        return account;
    }

    private void storeHAServerData(Message message) {
        int fixTag10019 = -1;
        try {
            if (message.isSetField(FixConstants.FIX_TAG_SERVED_BY)) {
                if (message.isSetField(FixConstants.FIX_TAG_10019_TRADING_ACC_TYPE)) {
                    fixTag10019 =  message.getInt(FixConstants.FIX_TAG_10019_TRADING_ACC_TYPE);
                }
                int servedBy = message.getInt(FixConstants.FIX_TAG_SERVED_BY);
                storeServedById(message, fixTag10019, servedBy);
                message.removeField(FixConstants.FIX_TAG_SERVED_BY);
            }
        } catch (FieldNotFound fnf) {
            logger.error("Error at storing HA Server Data: " + fnf.getMessage(), fnf);
        }
    }

    private void storeServedById(Message message, int fixTag10019, int servedBy) throws FieldNotFound {
        String account = getAccToPopulateInterQueueData(message);
        account = formatAccountString(account);
        if (account != null) {
            if(fixTag10019 == IConstants.CONSTANT_TWO_2){
                nonDisclosedTrdAccntSet.add(account);
                //when another customer use same parent account to make a non-disclosure type order ,
                //original parent account mapping is removed to avoid collision
                if (accountFixTag10008.keySet().contains(account)) {
                    logger.debug("fixTag10008 removed against account: " + accountFixTag10008.get(account) + " - " + account);
                    accountFixTag10008.remove(account);
                }
            }
            if (accountFixTag10008.keySet().contains(account)) {
                fixTag10008ServedBy.put(accountFixTag10008.get(account), servedBy);
                logger.debug("servedBy stored against fixTag10008: " + accountFixTag10008.get(account) + " - " + servedBy);
            }
        } else if (message.isSetField(FixConstants.FIX_TAG_CLIENT_REQUEST_ID)) {
            String requestId = message.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID);
            requestIdServedBy.put(requestId, servedBy);
            logger.debug("servedBy stored against requestId: " + requestId + " - " + servedBy);
        }
    }

    private void processTagModifications(Message message, SessionID sessionID, boolean isRemoveTag) throws ConfigError, FieldNotFound {
        Properties prop = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true);
        if (prop.containsKey(IConstants.SETTING_TAG_MODIFICATION)
                && prop.get(IConstants.SETTING_TAG_MODIFICATION).toString().equalsIgnoreCase(IConstants.SETTING_YES)) {
            for (Map.Entry<Object, Object> entry : prop.entrySet()) {
                String key = entry.getKey().toString();
                String value = entry.getValue().toString();
                if (key.startsWith(IConstants.SETTING_TAG_MODIFICATION_TAG)
                        && !key.equalsIgnoreCase(IConstants.SETTING_TAG_MODIFICATION)) {
                    int modifyTag = Integer.parseInt(key.substring(3));//remove 'Tag'
                    processEntry(modifyTag, value, message, isRemoveTag);
                }
            }
        }
    }

    private void changeTargetCompID(Message message, SessionID sessionID) throws ConfigError {
        Properties prop = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true);
        if (prop.containsKey(IConstants.SETTING_APPMSG_TARGETCOMPID)) {
            String newCompID = prop.get(IConstants.SETTING_APPMSG_TARGETCOMPID).toString();
            if (newCompID != null && !newCompID.isEmpty()) {
                final Message.Header header = message.getHeader();
                header.removeField(56);
                header.setString(56, newCompID);
            }
        }
    }

    private void addRepeatingGroup(Message message, SessionID sessionID) throws ConfigError {
        String reorderMsgTags;
        String tagValue;
        Properties prop = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true);
        List<Integer> ordList = new ArrayList<>();
        final Message.Header header = message.getHeader();
        if (prop.containsKey(IConstants.SETTING_REPEATING_GRP_TAGS)) {
            reorderMsgTags = prop.get(IConstants.SETTING_REPEATING_GRP_TAGS).toString();
            StringTokenizer tags = new StringTokenizer(reorderMsgTags, "|");
            while (tags.hasMoreTokens()) {
                ordList.add(Integer.parseInt(tags.nextToken()));
            }
            try {
                if (header.getField(new MsgType()).valueEquals(MsgType.ORDER_SINGLE)
                        || header.getField(new MsgType()).valueEquals(MsgType.ORDER_CANCEL_REPLACE_REQUEST)) {
                    int[] ord = new int[ordList.size() - 1];
                    for (int i = 1; i < ordList.size(); i++) {
                        ord[i - 1] = ordList.get(i);
                    }
                    Group g = new Group(ordList.get(0), ordList.get(0), ord);
                    for (int i = 0; i < ord.length; i++) {
                        tagValue = message.getString(ord[i]);
                        message.removeField(ord[i]);
                        g.setString(ord[i], tagValue);
                    }
                    message.addGroup(g);

                }
            } catch (Exception e) {
                logger.error("Error adding Repeating Group Tags: " + e.getMessage(), e);
            }
        }
    }

    public void populateOMSReqIdMap(Message message) {
        try {
            if (message.isSetField(FixConstants.FIX_TAG_OMS_REQ_ID) && message.isSetField(ClOrdID.FIELD)) {
                logger.elklog(message.getString(FixConstants.FIX_TAG_OMS_REQ_ID), LogEventsEnum.SENT_TO_EXCHANGE, "Sent to Exchange ClOrdID:" + message.getString(ClOrdID.FIELD));
                clOrdIdOmsReqIdMap.put(message.getString(ClOrdID.FIELD), message.getString(FixConstants.FIX_TAG_OMS_REQ_ID));
                message.removeField(FixConstants.FIX_TAG_OMS_REQ_ID);
            }
        } catch (Exception e) {
            logger.error("OMS Request Id storing failed:" + e.getMessage());
        }
    }

    private void clearUnwantedData(Message message) {
        try {
            if (message.isSetField(ClOrdID.FIELD) && message.isSetField(OrdStatus.FIELD)) {
                char ordStatus = message.getChar(OrdStatus.FIELD);
                String clOrdId = null;
                switch (ordStatus) {
                    case OrdStatus.FILLED:
                    case OrdStatus.REJECTED:
                    case OrdStatus.EXPIRED:
                        clOrdId = message.getString(ClOrdID.FIELD);
                        break;
                    case OrdStatus.REPLACED:
                    case OrdStatus.CANCELED:
                        if (message.isSetField(OrigClOrdID.FIELD)) {
                            clOrdId = message.getString(OrigClOrdID.FIELD);
                        }
                        break;
                    default:
                        logger.trace(" Invalid Order status " + ordStatus + " for clearUnwantedData");
                        break;
                }
                removeFromCache(clOrdId, ordStatus);
                if (ordStatus == OrdStatus.CANCELED) {
                    clOrdId = message.getString(ClOrdID.FIELD);
                    removeFromCache(clOrdId, ordStatus);
                }
            }
        } catch (FieldNotFound e) {
            logger.error("Error clearing DFIX Map Data: " + e.getMessage(), e);
        }
    }

    private void removeFromCache(String key, char ordStatus) {
        if (key != null) {
            if (clOrdIdFixTag10008.containsKey(key)) {
                int fixTag10008 = clOrdIdFixTag10008.remove(key);
                logger.debug("fixTag10008 Removed against ClOrdid: " + fixTag10008 + " - " + key + " - " + ordStatus);
            }
            if (clOrdIdOmsReqIdMap.containsKey(key)) {
                String omsReqId = clOrdIdOmsReqIdMap.remove(key);
                logger.debug("fixTag10008 Removed against ClOrdid: " + omsReqId + " - " + key + " - " + ordStatus);
            }
            if (isSimProcessICMOrders() && icmOrdIdQueueIdMap.containsKey(key)) {
                logger.debug("Clearing order from icmOrdIdQueueIdMap:"+key);
                icmOrdIdQueueIdMap.remove(key);
            }
            if (isSimProcessICMOrders() && icmRemoteOrdIdQueueIdMap.containsKey(key)) {
                logger.debug("Clearing order from icmRemoteOrdIdQueueIdMap:"+key);
                icmRemoteOrdIdQueueIdMap.remove(key);
            }
        }
    }

    HashMap<String, Integer> getClOrdIdFixTag10008() {
        return clOrdIdFixTag10008;
    }


    HashMap<String, Integer> getIcmOrdIdQueueIdMap() {
        return icmOrdIdQueueIdMap;
    }

    HashMap<String, Integer> getIcmRemoteOrdIdQueueIdMap() {
        return icmRemoteOrdIdQueueIdMap;
    }



    private boolean checkFlowControl(Message message, SessionID sessionID, boolean isFromFix) {
        boolean isMessageAllowed = true;
        String sessionIdentifier = getSessionIdentifier(sessionID);
        try {
            if (FIXClient.getFIXClient().getFlowControllers().containsKey(sessionID)) {
                FlowController flowController = FIXClient.getFIXClient().getFlowControllers().get(sessionID);
                FlowControlStatus flowControlStatus = FlowControlStatus.ALLOWED;
                if (message.getHeader().getString(MsgType.FIELD).equals(NewOrderSingle.MSGTYPE)
                        || message.getHeader().getString(MsgType.FIELD).equals(OrderCancelReplaceRequest.MSGTYPE)) {
                    if (!flowController.isAlive()) {
                        FIXClient.getFIXClient().getFlowControlPool().scheduleAtFixedRate(flowController, 0, 1, TimeUnit.SECONDS);
                    }
                    flowControlStatus = flowController.isSendMessage(message, isFromFix);
                }
                isMessageAllowed = (flowControlStatus == FlowControlStatus.ALLOWED);
                flowControlForwardRejectMessage(message, sessionID, isFromFix, isMessageAllowed, flowControlStatus, sessionIdentifier);
            }
        } catch (FieldNotFound | IllegalThreadStateException ex) {
            logger.error("Error at Checking FLow Control: " + ex.getMessage(), ex);
        }
        return isMessageAllowed;
    }

    private void flowControlForwardRejectMessage(Message message, SessionID sessionID, boolean isFromFix, boolean isMessageAllowed, FlowControlStatus flowControlStatus, String sessionIdentifier) throws FieldNotFound {
        if (!isMessageAllowed && flowControlStatus != FlowControlStatus.CLIORDID_DUPLICATE_BLOCKED ) {
//                  only forward reject response to OMS/exchange if flowControlStatus is not CLIORDID_DUPLICATE_BLOCKED ( not rejected due to duplicate CliOrdId )
            String isRejectMessage = FIXClient.getFIXClient().getSessionProperty(sessionID, IConstants.SETTING_TAG_IS_REJECT_MESSAGE);
            DFIXMessage dfixMsg = new DFIXMessage();
            dfixMsg.setExchange(sessionIdentifier);
            dfixMsg.setSequence(getExecutionId());
            dfixMsg.setType(Integer.toString(IConstants.APPLICATION_MESSAGE_RECEIVED));
            if (isRejectMessage == null || IConstants.SETTING_YES.equals(isRejectMessage)) {
                String rejectReason = flowControlStatus.getMessage()
                        + " in Session: " + sessionIdentifier;
                Message rejectMessage = getRejectMessage(message, rejectReason);
                rejectMessage.setString(SenderCompID.FIELD, sessionID.getSenderCompID());
                rejectMessage.setString(TargetCompID.FIELD, sessionID.getTargetCompID());
                if (sessionID.getTargetSubID() != null) {
                    rejectMessage.setString(TargetSubID.FIELD, sessionID.getTargetSubID());
                }
                if (sessionID.getSenderSubID() != null) {
                    rejectMessage.setString(SenderSubID.FIELD, sessionID.getSenderSubID());
                }
                sendDfixMsgForForwardRejectMsg(dfixMsg, rejectMessage);
                if (isFromFix) {
                    sendToTarget(rejectMessage, sessionID);
                }
            } else {
                dfixMsg.setMessage(message.toString());
                DFIXRouterManager.getToExchangeQueue().addMsg(dfixMsg.composeMessage());
            }
        }
    }

    private void sendDfixMsgForForwardRejectMsg(DFIXMessage dfixMsg, Message rejectMessage) throws FieldNotFound {
        if (DFIXRouterManager.getFromExchangeQueue() != null) {
            dfixMsg.setFixMsgType(rejectMessage.getHeader().getString(MsgType.FIELD));
            populateIntermediateQueueData(rejectMessage, dfixMsg);
            dfixMsg.setMessage(rejectMessage.toString());
            populateHAData(rejectMessage, dfixMsg);
            setMsgGroupId(dfixMsg, rejectMessage);
            clearUnwantedData(rejectMessage);
            DFIXRouterManager.getFromExchangeQueue().addMsg(dfixMsg);
        }
    }

    private void processEntry(int modifyTag, String modifyTagValue, Message message, boolean isRemoveTag) throws FieldNotFound {
        if (message.isSetField(modifyTag)) {
            if (isRemoveTag) {
                modifyTagValue = message.getString(modifyTag).replace(modifyTagValue, "");
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(modifyTagValue).append(message.getString(modifyTag));
                modifyTagValue = sb.toString();
            }
            message.removeField(modifyTag);
            message.setString(modifyTag, modifyTagValue);
        }
    }

    public void populateIntermediateQueueData(Message message, DFIXMessage dfixMessage) {
        try {
            int fixTag10008 = getFixTag10008ToPopulateIntermediateQueueData(message, dfixMessage);
            simProcessICMOrdToPopulateIntermediateQueueData(message, dfixMessage);
            if (isWatchdogEnabled()) {
                if (fixTag10008 > 0) {  //Send this field back to OMS
                    message.setField(new IntField(FixConstants.FIX_TAG_10008, fixTag10008));
                }
            } else {
                dfixMessage.setFixTag10008(fixTag10008);
            }
        } catch (FieldNotFound fieldNotFound) {
            logger.error("Error Populating tag 10008 for executions: " + fieldNotFound.getMessage(), fieldNotFound);
        }

    }

    private int getFixTag10008ToPopulateIntermediateQueueData(Message message, DFIXMessage dfixMessage) throws FieldNotFound {
        int fixTag10008 = -1;
        final String fixMsgType = dfixMessage.getFixMsgType();
        if (!MsgType.ORDER_SINGLE.equals(fixMsgType)) {
            if (message.isSetField(OrigClOrdID.FIELD)) {
                String origClOrdId = message.getString(OrigClOrdID.FIELD);
                if (clOrdIdFixTag10008.containsKey(origClOrdId)) {
                    fixTag10008 = clOrdIdFixTag10008.get(origClOrdId);
                }
            }
            if (fixTag10008 < 0 && message.isSetField(ClOrdID.FIELD)) {
                String clOrdId = message.getString(ClOrdID.FIELD);
                if (clOrdIdFixTag10008.containsKey(clOrdId)) {
                    fixTag10008 = clOrdIdFixTag10008.get(clOrdId);
                }
            }
        }
        String account = getAccToPopulateInterQueueData(message);
        account = formatAccountString(account);
        if (account == null) {
            return fixTag10008;
        }
        dfixMessage.setSimProcessing(isSimProcessing(message, dfixMessage, fixMsgType, account));

        if (fixTag10008 < 0 && accountFixTag10008.keySet().contains(account)) {
            fixTag10008 = accountFixTag10008.get(account);
        }
        return fixTag10008;
    }

    private boolean isSimProcessing(Message message, DFIXMessage dfixMessage, String fixMsgType, String account) throws FieldNotFound {
        if (fixMsgType!=null) {
            return Settings.isSimultaneousOrdReqOrdQueProcessing()
                    && ((fixMsgType.equals(MsgType.ORDER_SINGLE) && simOrderProcess.contains(account))
                    || ExchangeMessageProcessorFactory.isAllowedParallelProcessing(message, dfixMessage.getExchange()));
        }
        return false;
    }

    private String getAccToPopulateInterQueueData(Message message) throws FieldNotFound {
        String account = null;
        if (message.isSetField(Account.FIELD)) {
            account = message.getString(Account.FIELD);
        }
        if (message.hasGroup(NoPartyIDs.FIELD)) {
            for (Group group : message.getGroups(NoPartyIDs.FIELD)) {
                if (group.isSetField(PartyRole.FIELD) && group.getInt(PartyRole.FIELD) == PartyRole.CLIENT_ID) {
                    account = group.getString(PartyID.FIELD);
                }
            }
        }
        return account;
    }

    private void simProcessICMOrdToPopulateIntermediateQueueData(Message message, DFIXMessage dfixMessage) throws FieldNotFound {
        if (isSimProcessICMOrders() && message.isSetField(ClOrdID.FIELD)) { // Order-wise Queue Parallelization
            String orderIdICM = message.getString(ClOrdID.FIELD);
            if (icmOrdIdQueueIdMap.containsKey(orderIdICM)) {
                dfixMessage.setInterQueueId(icmOrdIdQueueIdMap.get(orderIdICM));
                logger.debug("Loading from icmOrdIdQueueIdMap :" + orderIdICM + ", Queue:" + dfixMessage.getInterQueueId());
            } else if (message.isSetField(OrigClOrdID.FIELD)) {
                String oriOrdIdICM = message.getString(OrigClOrdID.FIELD);
                if (icmRemoteOrdIdQueueIdMap.containsKey(oriOrdIdICM)) {
                    dfixMessage.setInterQueueId(icmRemoteOrdIdQueueIdMap.get(oriOrdIdICM));
                    logger.debug("Loading from icmRemoteOrdIdQueueIdMap :" + oriOrdIdICM + ", Queue:" + dfixMessage.getInterQueueId());
                    // To Handle multiple amend/cancel
                    icmRemoteOrdIdQueueIdMap.computeIfAbsent(oriOrdIdICM, k -> dfixMessage.getInterQueueId());
                } else if (icmOrdIdQueueIdMap.containsKey(oriOrdIdICM)) {//eg:39=4
                    dfixMessage.setInterQueueId(icmOrdIdQueueIdMap.get(oriOrdIdICM));
                    logger.debug("Loading from icmOrdIdQueueIdMap :" + oriOrdIdICM + ", Queue:" + dfixMessage.getInterQueueId());
                }
            }
            if (dfixMessage.getInterQueueId() > -1) {
                dfixMessage.setSimProcessing(true);
            }
        }
    }

    public void populateHAData(Message message, DFIXMessage dfixMessage) throws FieldNotFound {
        int servedBy = -1;
        if (isWatchdogEnabled()) {  //Resolve server id from watchdog agent
            servedBy = WatchDogHandler.getAppServerId(message);
        } else if (dfixMessage.getFixTag10008() != -1
                && fixTag10008ServedBy.containsKey(dfixMessage.getFixTag10008())) {
            int omsId = fixTag10008ServedBy.get(dfixMessage.getFixTag10008());
            servedBy = Settings.getHostIdForOmsId(omsId);
        } else if (message.isSetField(FixConstants.FIX_TAG_CLIENT_REQUEST_ID)
                && dfixMessage.getFixMsgType() != null
                && dfixMessage.getFixMsgType().charAt(0) == FixConstants.FIX_VALUE_35_USER_DEFINED
                && requestIdServedBy.containsKey(message.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID))) {
            servedBy = requestIdServedBy.get(message.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID));
            requestIdServedBy.remove(message.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID));
        } else if (dfixMessage.getFixMsgType().equals(MsgType.TRADING_SESSION_STATUS)
                || dfixMessage.getFixMsgType().equals(MsgType.TRADE_CAPTURE_REPORT)
                || dfixMessage.getFixMsgType().equals(MsgType.TRADE_CAPTURE_REPORT_ACK)
                || (dfixMessage.getFixMsgType().equals(MsgType.EXECUTION_REPORT) && !message.isSetField(ClOrdID.FIELD))) {
            servedBy = 1;
        }
        dfixMessage.setServedBy(servedBy);
    }

    private void processRejectionMessages(Message rejection, SessionID sessionID) {
        final Message.Header rejectionHeader = rejection.getHeader();
        try {
            if ((rejectionHeader.getField(new MsgType()).valueEquals(MsgType.REJECT)
                    || rejectionHeader.getField(new MsgType()).valueEquals(MsgType.BUSINESS_MESSAGE_REJECT))
                    && !rejection.isSetField(ClOrdID.FIELD)) {
                List<Message> messages = FIXClient.getFIXClient().getMessages(sessionID, rejection.getInt(RefSeqNum.FIELD), rejection.getInt(RefSeqNum.FIELD));
                if (!messages.isEmpty() && messages.get(0) != null) { //request != null
                    Message request = messages.get(0);
                    populateCliOrdIdForRejectionMsg(rejection, request);
                }
            }
        } catch (Exception e) {
            logger.error("Error Processing Reject Execution Report: " + e.getMessage(), e);
        }
    }

    private void populateCliOrdIdForRejectionMsg(Message rejection, Message request) throws FieldNotFound {
        String clOrdId = null;
        final StringField msgTypeField = request.getHeader().getField(new MsgType());
        if (msgTypeField.valueEquals(MsgType.ORDER_SINGLE)
                || msgTypeField.valueEquals(MsgType.ORDER_CANCEL_REPLACE_REQUEST)
                || msgTypeField.valueEquals(MsgType.ORDER_CANCEL_REQUEST)) {
            clOrdId = request.getString(ClOrdID.FIELD);
        } else if (request.isSetField(FixConstants.FIX_TAG_CLIENT_REQUEST_ID)
                && request.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID) != null) {
            clOrdId = request.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID);
        }
        if (clOrdId != null) {
            rejection.setField(new ClOrdID(clOrdId));
        }
    }

    public void addLogonField(Message message, SessionID sessionID) throws ConfigError {
        String logonString;
        Properties prop = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true);
        if (prop.containsKey(IConstants.SETTING_LOGON_USER_DEFINED_TAGS)) {
            logonString = prop.get(IConstants.SETTING_LOGON_USER_DEFINED_TAGS).toString();
            StringTokenizer tags = new StringTokenizer(logonString, "|");
            while (tags.hasMoreTokens()) {
                String[] keyValue = tags.nextToken().split("=");
                int key = Integer.parseInt(keyValue[0]);
                message.setField(new StringField(key, keyValue[1]));
            }
        }
    }

    public void sendLinkStatusDisconnected(String dfixId, SessionID sessionId) {
        if (isWatchdogEnabled()) {
            String sessionNo = "EXCHANGE-" + sessionNoData.get(sessionId.toString());
            String sessionIdentifier = getSessionIdentifier(sessionId);
            //inline import is used since watchdog is not used for GBL at runtime
            WatchDogHandler.sendExchangeLinkStatus(dfixId, sessionNo, com.dfn.watchdog.commons.State.CLOSED, sessionIdentifier);
        }
    }

    // LKCSE: Taken out this code block for reusability
    private void sendAdminMessage(String sessionIdentifier, String newStatus) {
        UIAdminServer.sendToAdminClients(sessionIdentifier, newStatus);
    }

    public void sendLinkStatusConnected(String dfixId, SessionID sessionId) {
        if (isWatchdogEnabled()) {
            String sessionNo = "EXCHANGE-" + sessionNoData.get(sessionId.toString());
            String sessionIdentifier = getSessionIdentifier(sessionId);
            //inline import is used since watchdog is not used for GBL at runtime
            WatchDogHandler.sendExchangeLinkStatus(dfixId, sessionNo, com.dfn.watchdog.commons.State.CONNECTED, sessionIdentifier);
        }
    }

    public String getSessionIdentifier(SessionID sessionID) {
        return FIXClient.getFIXClient().getSessionProperty(sessionID, IConstants.SESSION_IDENTIFIER);
    }

    public void populateSessionNoData(SessionID sessionID) {
        if (!sessionNoData.containsKey(sessionID.toString())) {
            int sesNumber = sessionNoData.size() + 1;
            sessionNoData.put(sessionID.toString(), sesNumber);
        }
    }

    @Override
    public boolean canLogon(SessionID sessionID) {
        return IConstants.SETTING_YES.equalsIgnoreCase(autoConnect)
                || (FIXClient.getFIXClient() != null
                && FIXClient.getFIXClient().getInitiatorSessionList().contains(sessionID));
    }

    @Override
    public void onBeforeSessionReset(SessionID sessionID) {
        /*
         * This is not used and implemented.
         * */
    }

    public Map<String, Long> getOrderLastExecutionTimeData() {
        return orderLastExecutionTimeData;
    }

    public Map<String, Integer> getIntermediateQueueData() {
        return accountFixTag10008;
    }

    public Map<Integer, Integer> getHAServerData() {
        return fixTag10008ServedBy;
    }

    Map<String, Integer> getSessionNoData() {
        return sessionNoData;
    }

    public void clearCache() {
        logger.debug("Cache Clear: " + new Date());
        accountFixTag10008.clear();
        fixTag10008ServedBy.clear();
        simOrderProcess.clear();
        nonDisclosedTrdAccntSet.clear();
    }

    Map<String, String> getClOrdIdOmsReqIdMap() {
        return clOrdIdOmsReqIdMap;
    }

    /**
     * @param message
     * @return boolean ( whether to send to oms or not )
     * <p>
     * Store OpenOrders with HA Details
     */
    public boolean storeOpenOrdersWithHADetails(Message origMessage) {
        try {
            if (origMessage.getHeader().getString(MsgType.FIELD).equals(FixConstants.FIX_VALUE_35_DFIX_INFORMATION)
                    && origMessage.isSetField(ListID.FIELD)
                    && origMessage.getInt(ListID.FIELD) == FixConstants.FIX_TAG_HA_DETAIL) {
                String fixMessage = origMessage.toString();
                String sToken;
                String sTag;
                DataDisintegrator dataDisintegrator = new DataDisintegrator("=");
                StringTokenizer st = new StringTokenizer(fixMessage, IConstants.FD);
                Message message = null;
                logger.debug("parseFIXMessage() FIXMessage : " + fixMessage);
                while (st.hasMoreTokens()) {
                    sToken = st.nextToken();
                    dataDisintegrator.setData(sToken);
                    sTag = dataDisintegrator.getTag();
                    message = processTokensForOrdersWIthHADetails(sTag, dataDisintegrator, message);
                }
                return false;
            }
        } catch (FieldNotFound fieldNotFound) {
            logger.error("Couldn't find field ", fieldNotFound);
        }
        return true;
    }

    private Message processTokensForOrdersWIthHADetails(String sTag, DataDisintegrator dataDisintegrator, Message message) {
        int iTag;
        String sValue;
        if (sTag != null) {
            try {
                iTag = Integer.parseInt(sTag);
                sValue = dataDisintegrator.getData();
                if (iTag == FixConstants.FIX_TAG_10008) {
                    message = new Message();
                    message.setString(FixConstants.FIX_TAG_10008, sValue);
                } else if (message != null) {
                    switch (iTag) {
                        case Account.FIELD:
                            message.setString(Account.FIELD, sValue);
                            storeIntermediateQueueData(message, false);
                            break;
                        case ClOrdID.FIELD:
                            message.setString(ClOrdID.FIELD, sValue);
                            storeIntermediateQueueData(message, false);
                            break;
                        case FixConstants.FIX_TAG_SERVED_BY:
                            message.setString(FixConstants.FIX_TAG_SERVED_BY, sValue);
                            storeHAServerData(message);
                            break;
                        case FixConstants.FIX_TAG_10014:
                            message.setString(FixConstants.FIX_TAG_10014, sValue);
                            storeIntermediateQueueData(message, false);
                            break;
                        default:
                            break;
                    }
                }
            } catch (NumberFormatException e) {
                logger.error("storeOpenOrdersWithHADetails error parseFIXMessage ", e);
            }
        }
        return message;
    }

    private boolean handleLoggingEvent(int targetSessionStatus, SessionID sessionID) {
        boolean isSend = true;
        try {
            String sessionIdentifier = getSessionIdentifier(sessionID);
            Message message = new Message();
            DataDictionary dict = FIXClient.getFIXClient().getDictionaryMap().get(sessionIdentifier);
            DataDictionary appDataDict;
            String fixBody = buildSessionStatusResponse(targetSessionStatus);
            if (sessionID.isFIXT()) {
                appDataDict = FIXClient.getFIXClient().getDictionaryMap().get("APP_" + sessionIdentifier);
                message.fromString(fixBody, dict, appDataDict, false);
            } else {
                message.fromString(fixBody, dict, false);
            }
            addCustomFieldsToOMS(FIXClient.getSettings().get(sessionID), message, sessionID);
            Properties prop = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true);
            isSend = handleNonOMSMessages(message, prop);
        } catch (Exception e) {
            logger.error("Error at handleLoggingEvent: " + e.getMessage(), e);
        }
        return isSend;
    }

    private String buildSessionStatusResponse(int targetSessionStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append(MsgType.FIELD).append("=").append(FixConstants.FIX_VALUE_35_DFIX_INFORMATION).append(IConstants.FD);
        sb.append(ListID.FIELD).append("=").append(targetSessionStatus).append(IConstants.FD);
        return sb.toString();
    }

    HashMap<String, Integer> getAccountFixTag10008() {
        return accountFixTag10008;
    }

    HashMap<Integer, Integer> getFixTag10008ServedBy() {
        return fixTag10008ServedBy;
    }

    Set<String> getNonDisclosedTrdAccntSet() {
        return nonDisclosedTrdAccntSet;
    }


    Set<String> getSimOrderProcess() {
        return simOrderProcess;
    }

    boolean isWatchdogEnabled(){
        return watchdogEnabled;
    }

    boolean isSimProcessICMOrders() {
        return isSimProcessICMOrders;
    }

    public void populateICMDropCopyQueueData(Message message, SessionID sessionID) {
        try {
            if (sessionID != null) {
                String icmDropCopySession = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true).getProperty(IConstants.SETTING_ICM_DROP_COPY_SESSION);
                if (IConstants.SETTING_YES.equalsIgnoreCase(icmDropCopySession) && message.isSetField(ClOrdID.FIELD) && (MsgType.EXECUTION_REPORT.equals(message.getHeader().getString(MsgType.FIELD)))){
                    String orderIdICM = message.getString(ClOrdID.FIELD);
                    if (!icmOrdIdQueueIdMap.keySet().contains(orderIdICM) && message.getChar(OrdStatus.FIELD) == OrdStatus.NEW && message.getChar(ExecType.FIELD) == ExecType.NEW) {
                        int innerQueueId = DFIXRouterManager.getFromExchangeQueue().getIntermediateQueueId(0);
                        icmOrdIdQueueIdMap.put(orderIdICM, innerQueueId);
                        icmRemoteOrdIdQueueIdMap.put(orderIdICM, innerQueueId);
                        logger.debug("Allocated Queue for Drop-copy :"+orderIdICM + ", "+innerQueueId);
                    } else if (icmRemoteOrdIdQueueIdMap.keySet().contains(orderIdICM)) {
                        icmOrdIdQueueIdMap.put(orderIdICM, icmRemoteOrdIdQueueIdMap.get(orderIdICM));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exception occurred in populateICMDropCopyQueueData,"+"sessionID:"+sessionID +", error="+ e.getMessage());
        }
    }
}

