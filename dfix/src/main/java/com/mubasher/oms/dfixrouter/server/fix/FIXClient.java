package com.mubasher.oms.dfixrouter.server.fix;

/**
 * Created by IntelliJ IDEA.
 * User: chamindah
 * Date: Oct 26, 2007
 * Time: 1:51:02 PM
 * To change this template use File | Settings | File Templates.
 */

import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.failover.DisconnectedSessionMessageHandler;
import com.mubasher.oms.dfixrouter.server.fix.flowcontrol.FlowController;
import com.mubasher.oms.dfixrouter.server.fix.simulator.FIXSimApplication;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.ValidateSessions;
import quickfix.*;
import quickfix.Dictionary;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

//Dasun Perera

public class FIXClient {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.FIXClient");
    private static Map<SessionID, SessionSettings> settings = new HashMap<>();
    private static SessionSettings simSettings = null;
    private static FIXClient fixClient = null;
    private static boolean initiatorStarted = false;
    private static boolean acceptorStarted = false;
    private Map<SessionID, MessageStoreFactory> storeFactoryMap = new HashMap<>();
    private Map<SessionID, MessageFactory> messageFactoryMap = new HashMap<>();
    private Map<SessionID, LogFactory> logFactoryMap = new HashMap<>();
    private FIXApplication application;
    private Map<SessionID, Initiator> initiators = new HashMap<>();
    private Map<SessionID, Acceptor> acceptors = new HashMap<>();
    private ThreadedSocketInitiator simInitiator = null;
    private ThreadedSocketAcceptor simAcceptor = null;
    private HashMap<String, SessionID> sessionIDMap = new HashMap<>();
    private HashMap<String, com.objectspace.jgl.Queue> pendingMessagesMap = new HashMap<>();
    private HashMap<String, DataDictionary> dictionaryMap = new HashMap<>();
    private HashMap<SessionID, FlowController> flowControllers = new HashMap<>();
    private int longestLengthSessionIdentifier = 0;
    private int longestLengthTargetCompId = 0;
    private int longestLengthSenderCompId = 0;
    private ExecutorService executorService;
    private ScheduledExecutorService flowControlPool;
    private FIXSimApplication fixSimApplication;
    private HashSet<SessionID> manuallyLoggedOutAcceptors = new HashSet<>();

    private FIXClient() {
    }

    public static FIXClient getFIXClient() {
        if (fixClient == null)
            fixClient = new FIXClient();
        return fixClient;

    }

    public void startFIXClient() {
        if (DFIXRouterManager.isFixClientEnable()) {
            logger.debug("Initializing FIX Client....");
            try (FileInputStream in = new FileInputStream(Settings.getCfgFileName())) {
                initiateFIXClients(in);
            } catch (Exception e) {
                logger.error("Error Starting FIX Client: " + e.getMessage(), e);
            }
        }
    }
    private void initiateFIXClients(FileInputStream in) {
        try {
            validateSessions(Settings.getCfgFileName());
            SessionSettings sesSettings = getSessionSettings(in);
            setSettings(sesSettings);
            MessageFactory messageFactory = new DefaultMessageFactory();
            application = new FIXApplication();
            Iterator<SessionID> it = sesSettings.sectionIterator();
            while (it.hasNext()) {
                initFixSessions(it, messageFactory);
            }
            loadCache();   // sessionIDMap and dictionaryMap
        } catch (ConfigError ce) {
            logger.error("Error Initializing FIX Client: " + ce.getMessage(), ce);
        }
    }

    private void initFixSessions(Iterator<SessionID> it, MessageFactory messageFactory) {
        try{
            while (it.hasNext()) {
                SessionID sessionID = it.next();
                SessionSettings sessionSettings = getSettings().get(sessionID);
                LogFactory logFactory = getFileLogFactory(sessionSettings);
                MessageStoreFactory messageStoreFactory = new FileStoreFactory(sessionSettings);
                storeFactoryMap.put(sessionID, messageStoreFactory);
                logFactoryMap.put(sessionID, logFactory);
                messageFactoryMap.put(sessionID, messageFactory);
                if (IConstants.INITIATOR_CONNECTION_TYPE.equalsIgnoreCase(sessionSettings.getString(sessionID, IConstants.SETTING_CONNECTION_TYPE))) {
                    logger.info("Configuring initiator sessions");
                    initiators.put(sessionID, new ThreadedSocketInitiator(application, messageStoreFactory, sessionSettings, logFactory, messageFactory));
                } else {
                    logger.info("Configuring acceptor sessions");
                    acceptors.put(sessionID, new ThreadedSocketAcceptor(application, messageStoreFactory, sessionSettings, logFactory, messageFactory));
                }
            }
        } catch (Exception e) {
            logger.error("Error init FIX Sessions: " + e.getMessage(), e);
        }
    }

    private void validateSessions(String cfgFileName) throws ConfigError {
        try (FileInputStream in = new FileInputStream(cfgFileName)) {
            ValidateSessions.getInstance().load(in);
        } catch (IOException e) {
            logger.error("Error validating FIX Sessions: " + e.getMessage(), e);
        }
    }

    private SessionSettings getSessionSettings(FileInputStream in) throws ConfigError {
        return new SessionSettings(in);
    }

    private LogFactory getFileLogFactory(SessionSettings sessionSettings) {
        LogFactory logFactory = new FileLogFactory(sessionSettings);
        if (Settings.getInt(SettingsConstants.FIX_LOG_TYPE) == IConstants.SETTING_FIX_LOG_TYPE_SEPERATE_FILE) {
            sessionSettings.getDefaultProperties().put("SLF4JLogPrependSessionID", IConstants.SETTING_YES);
            Iterator<SessionID> it = sessionSettings.sectionIterator();

            try {
                while (it.hasNext()) {
                    SessionID sessionID = it.next();
                    String sessionId = sessionSettings.getSessionProperties(sessionID, false).getProperty(IConstants.SESSION_IDENTIFIER);
                    sessionSettings.getSessionProperties(sessionID).put("SLF4JLogEventCategory", sessionId + ".event");
                    sessionSettings.getSessionProperties(sessionID).put("SLF4JLogErrorEventCategory", sessionId + ".errorEvent");
                    sessionSettings.getSessionProperties(sessionID).put("SLF4JLogIncomingMessageCategory", sessionId + ".incoming");
                    sessionSettings.getSessionProperties(sessionID).put("SLF4JLogOutgoingMessageCategory", sessionId + ".outgoing");
                    Log4j2Handler.addSessionLogger(sessionId);
                }
            } catch (Exception e) {
                logger.error("Error creating FileLogFactory: " + e.getMessage(), e);
            }
            logFactory = new SLF4JLogFactory(sessionSettings);
        }
        return logFactory;
    }

    private void loadCache() throws ConfigError {
        logger.info("Creating Session FileStore:");
        Iterator<SessionID> it = getSettings().keySet().iterator();

        while (it.hasNext()) {
            DataDictionary dict;
            SessionID sessionID = it.next();
            String sessionIdentifier = getSessionProperty(sessionID, IConstants.SESSION_IDENTIFIER);
            if (sessionIdentifier.length() > longestLengthSessionIdentifier) {
                longestLengthSessionIdentifier = sessionIdentifier.length();
            }
            String senderCompId = sessionID.getSenderCompID();
            if (senderCompId.length() > longestLengthSenderCompId) {
                longestLengthSenderCompId = senderCompId.length();
            }
            String targetCompId = sessionID.getTargetCompID();
            if (targetCompId.length() > longestLengthTargetCompId) {
                longestLengthTargetCompId = targetCompId.length();
            }
            sessionIDMap.put(sessionIdentifier, sessionID);
            pendingMessagesMap.put(sessionIdentifier, new com.objectspace.jgl.Queue());
            if (sessionID.isFIXT()) {
                dict = getDataDictionary(getSessionProperty(sessionID, IConstants.SETTING_TRANSPORT_DATA_DICTIONARY));
                DataDictionary appDataDictionary = new DataDictionary(getSessionProperty(sessionID, IConstants.SETTING_APP_DATA_DICTIONARY));
                dictionaryMap.put(getSessionProperty(sessionID, IConstants.SESSION_IDENTIFIER), dict);
                dictionaryMap.put("APP_" + getSessionProperty(sessionID, IConstants.SESSION_IDENTIFIER), appDataDictionary);
            } else {
                dict = getDataDictionary(getSessionProperty(sessionID, IConstants.SETTING_DATA_DICTIONARY));
                dictionaryMap.put(getSessionProperty(sessionID, IConstants.SESSION_IDENTIFIER), dict);
            }
            populateFlowControlData(sessionID);
        }
        executorService = Executors.newFixedThreadPool(pendingMessagesMap.size());
        flowControlPool = Executors.newScheduledThreadPool(flowControllers.size());
    }

    public static Map<SessionID, SessionSettings> getSettings() {
        return settings;
    }

    public String getSessionProperty(SessionID sessionID, String property) {
        try {
            return getSettings().get(sessionID).getSessionProperties(sessionID, true).getProperty(property);
        } catch (Exception exception) {
            logger.error("Error at loading FIX Session Property:" + property + ":" + exception.getMessage(), exception);
            return null;
        }
    }

    public DataDictionary getDataDictionary(String sessionProperty) throws ConfigError {
        return new DataDictionary(sessionProperty);
    }

    private void populateFlowControlData(SessionID sessionID) {
        String flowConfig = getSessionProperty(sessionID, IConstants.SETTING_TAG_VALIDATE_MESSAGE_FLOW);
        if (IConstants.SETTING_YES.equals(flowConfig)) {
            int windowSize = getSessionProperty(sessionID, IConstants.SETTING_TAG_MESSAGE_FLOW_WINDOW_SIZE) != null
                    ? Integer.parseInt(getSessionProperty(sessionID, IConstants.SETTING_TAG_MESSAGE_FLOW_WINDOW_SIZE))
                    : IConstants.DEFAULT_FLOW_WINDOW_SIZE;
            FlowController flowController = new FlowController(windowSize);
            populateFlowControlDataForNewMsg(sessionID, flowController);
            populateFlowControlDataForAmendMsg(sessionID, flowController);
            populateFlowControlDataForDuplicateMsg(sessionID, flowController);
            // set duplicate detection method
            flowController.setBlockDuplicateByCliOrdId(Settings.getProperty(SettingsConstants.BLOCK_DUPLICATE_BY_CLIORDID).equals(IConstants.SETTING_YES));
            flowControllers.put(sessionID, flowController);
        }
    }

    private void populateFlowControlDataForDuplicateMsg(SessionID sessionID, FlowController flowController) {
        try {
            if (getSessionProperty(sessionID, IConstants.SETTING_TAG_DUPLICATE_WINDOW_LIMIT) != null) {
                flowController.setDuplicateMessageWindowLimit(Integer.parseInt(getSessionProperty(sessionID, IConstants.SETTING_TAG_DUPLICATE_WINDOW_LIMIT)));
            }
        } catch (NumberFormatException e) {
            logger.error("Error at populateFlowControlDataForDuplicateMsg: " + e.getMessage(), e);
        }
    }

    private void populateFlowControlDataForAmendMsg(SessionID sessionID, FlowController flowController) {
        try {
            if (getSessionProperty(sessionID, IConstants.SETTING_TAG_AMEND_MESSAGE_RATE) != null) {
                flowController.setAmendMessageRate(Integer.parseInt(getSessionProperty(sessionID, IConstants.SETTING_TAG_AMEND_MESSAGE_RATE)));
            }
            if (getSessionProperty(sessionID, IConstants.SETTING_TAG_AMEND_WINDOW_LIMIT) != null) {
                flowController.setAmendMessageWindowLimit(Integer.parseInt(getSessionProperty(sessionID, IConstants.SETTING_TAG_AMEND_WINDOW_LIMIT)));
            }
        } catch (NumberFormatException e) {
            logger.error("Error at populateFlowControlDataForAmendMsg: " + e.getMessage(), e);
        }
    }

    private void populateFlowControlDataForNewMsg(SessionID sessionID, FlowController flowController) {
        try {
            if (getSessionProperty(sessionID, IConstants.SETTING_TAG_NEW_MESSAGE_RATE) != null) {
                flowController.setNewMessageRate(Integer.parseInt(getSessionProperty(sessionID, IConstants.SETTING_TAG_NEW_MESSAGE_RATE)));
            }
            if (getSessionProperty(sessionID, IConstants.SETTING_TAG_NEW_WINDOW_LIMIT) != null) {
                flowController.setNewMessageWindowLimit(Integer.parseInt(getSessionProperty(sessionID, IConstants.SETTING_TAG_NEW_WINDOW_LIMIT)));
            }
        } catch (NumberFormatException e) {
            logger.error("Error at populateFlowControlDataForNewMsg: " + e.getMessage(), e);
        }
    }

    public static void setSettings(SessionSettings fullSettings) {
        Iterator<SessionID> it = fullSettings.sectionIterator();
        try {
            while (it.hasNext()) {
                SessionSettings sessionSettings = new SessionSettings();
                SessionID sessionID = it.next();
                sessionSettings.set(sessionID, new Dictionary("", fullSettings.getSessionProperties(sessionID, true)));
                settings.put(sessionID, sessionSettings);
            }
        } catch (Exception e) {
            logger.error("Error at setSettings: " + e.getMessage(), e);
        }
    }

    public void loginToFIXGateway() {
        logger.info("FIX Client login process started.");
        try {
            if (!initiatorStarted && !initiators.isEmpty()) {
                for (Initiator initiator : initiators.values()) {
                    logger.debug("Starting initiator sessions");
                    initiator.start();
                }
                setInitiatorStarted(true);
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("no initiators in settings")) {
                logger.error("Error at Starting initiator sessions" + e.getMessage(), e);
            }
        }
        try {
            if (!acceptorStarted && !acceptors.isEmpty()) {
                for (Acceptor acceptor : acceptors.values()) {
                    logger.debug("Starting acceptor sessions");
                    acceptor.start();
                }
                setAcceptorStarted(true);
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("No acceptor sessions found in settings.")) {
                logger.error("Error at Starting acceptor sessions" + e.getMessage(), e);
            }
        }
    }

    public static void setInitiatorStarted(boolean initiatorStarted) {
        FIXClient.initiatorStarted = initiatorStarted;
    }

    public static void setAcceptorStarted(boolean acceptorStarted) {
        FIXClient.acceptorStarted = acceptorStarted;
    }

    public List<SessionID> getAcceptorSessionList() {
        ArrayList<SessionID> sessionList = new ArrayList<>();
        sessionList.addAll(acceptors.keySet());
        return sessionList;
    }

    public void stopFIXClient() {
        logger.debug("Stopping FIX Client....");
        logoutFromFIXGateway();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (flowControlPool != null && !flowControlPool.isShutdown()) {
            flowControlPool.shutdown();
        }
    }

    public void logoutFromFIXGateway() {
        logger.info("Started logging out all sessions.");
        if (initiatorStarted) {
            for (Initiator initiator : initiators.values()) {
                initiator.stop();
            }
            setInitiatorStarted(false);
        }

        if (acceptorStarted) {
            for (Acceptor acceptor : acceptors.values()) {
                acceptor.stop();
            }
            setAcceptorStarted(false);
        }
        logger.info("FIX Client stopped!!!");
    }

    public boolean isLoggedIn() {
        return (initiatorStarted || acceptorStarted);
    }

    public void disconnectSession(String sessionIdentifier) {
        ArrayList<SessionID> initiatorSessionList = (ArrayList<SessionID>) getInitiatorSessionList();
        ArrayList<SessionID> acceptorSessionList = (ArrayList<SessionID>) getAcceptorSessionList();

        if (IConstants.ALL_SESSIONS.equalsIgnoreCase(sessionIdentifier)) {
            try {
                for (SessionID session : initiatorSessionList) {
                    logoutSession(session);
                }
            } catch (Exception e) {
                logger.error("Error at manual disconnect ALL initiator sessions: " + e.getMessage(), e);
            }
            try {
                for (SessionID session : acceptorSessionList) {
                    logoutSession(session);
                }
            } catch (Exception e) {
                logger.error("Error at manual disconnect ALL acceptor sessions: " + e.getMessage(), e);
            }
        } else {
            try {
                SessionID disconnectSession = getSessionID(sessionIdentifier);
                logoutSession(disconnectSession);
            } catch (Exception e) {
                logger.error("Error at manual disconnect " + sessionIdentifier + " session: " + e.getMessage(), e);
            }
        }
    }

    private void logoutSession(SessionID sessionID) {
        Session session = Session.lookupSession(sessionID);
        session.logout("user requested");
        if (!IConstants.INITIATOR_CONNECTION_TYPE.equals(getSessionProperty(sessionID, IConstants.SETTING_CONNECTION_TYPE))) {
            manuallyLoggedOutAcceptors.add(sessionID);
        }
        waitUntilDisconnect(session);
    }

    private void waitUntilDisconnect(Session session) {
        long a = 0;
        long waitTime = 10;
        String reconnectInterval = getSessionProperty(session.getSessionID(), IConstants.SETTING_RECONNECT_INTERVAL);
        long count = (session.getSessionID() == null || reconnectInterval == null) ? 0 : (Long.parseLong(reconnectInterval) * 1000/waitTime);
        while (session.isLoggedOn() && a < count){
            DFIXRouterManager.sleepThread(waitTime);
            ++a;
        }
    }

    public List<SessionID> getInitiatorSessionList() {
        ArrayList<SessionID> sessionList = new ArrayList<>();
        sessionList.addAll(initiators.keySet());
        return sessionList;
    }

    public SessionID getSessionID(String sessionIdentifier) {
        SessionID sessionID = null;
        if (sessionIDMap.containsKey(sessionIdentifier)) {
            sessionID = sessionIDMap.get(sessionIdentifier);
        }
        return sessionID;
    }

    public void connectSession(String sessionIdentifier) {
        ArrayList<SessionID> initiatorSessionList = (ArrayList<SessionID>) getInitiatorSessionList();

        if (IConstants.ALL_SESSIONS.equalsIgnoreCase(sessionIdentifier)) {
            for (SessionID sessionID : initiatorSessionList) {
                connectInitiatorSession(sessionID);
            }
            for (SessionID sessionID :
                    manuallyLoggedOutAcceptors) {
                connectAcceptorSession(sessionID);
            }
        } else {
            try {
                SessionID sessionID = getSessionID(sessionIdentifier);
                if (IConstants.INITIATOR_CONNECTION_TYPE.equals(getSessionProperty(sessionID, IConstants.SETTING_CONNECTION_TYPE))) {     //check if initiator session
                    connectInitiatorSession(sessionID);
                } else {
                    connectAcceptorSession(sessionID);
                }
            } catch (Exception e) {
                logger.error("Error at manual connect " + sessionIdentifier + " session: " + e.getMessage(), e);
            }
        }
    }

    private void connectAcceptorSession(SessionID sessionID) {
        Session session = Session.lookupSession(sessionID);
        manuallyLoggedOutAcceptors.remove(sessionID);
        waitUntilConnect(session);
    }

    private void waitUntilConnect(Session session) {
        long a = 0;
        long waitTime = 10;
        String reconnectInterval = getSessionProperty(session.getSessionID(), IConstants.SETTING_RECONNECT_INTERVAL);
        long count = (session.getSessionID() == null || reconnectInterval == null) ? 0 : (Long.parseLong(reconnectInterval) * 1000/waitTime);
        while (!session.isLoggedOn() && a < count){
            DFIXRouterManager.sleepThread(waitTime);
            ++a;
        }
    }

    private void connectInitiatorSession(SessionID sessionID) {
        try {
            Session session = Session.lookupSession(sessionID);
            session.logon();
            waitUntilConnect(session);
        } catch (Exception e) {
            logger.error("Error at manual connect initiator session" + e.getMessage(), e);
        }
    }

    public int getExpectedSenderSeqNumber(SessionID sessionID) {
        Session ses = Session.lookupSession(sessionID);
        return ses.getExpectedSenderNum();
    }

    public int getExpectedTargetSeqNumber(SessionID sessionID) {
        Session ses = Session.lookupSession(sessionID);
        return ses.getExpectedTargetNum();
    }

    public void runEOD(String sessionIdentifier) {
        ArrayList<SessionID> initiatorSessionList = (ArrayList<SessionID>) getInitiatorSessionList();
        try {
            if (IConstants.ALL_SESSIONS.equalsIgnoreCase(sessionIdentifier)) {
                for (SessionID session : initiatorSessionList) {
                    setOutGoingSeqNo(getSessionProperty(session, IConstants.SESSION_IDENTIFIER), 1);
                    setInComingSeqNo(getSessionProperty(session, IConstants.SESSION_IDENTIFIER), 1);
                }
            } else {
                setOutGoingSeqNo(sessionIdentifier, 1);
                setInComingSeqNo(sessionIdentifier, 1);
            }
        } catch (Exception e) {
            logger.error("Error at manual run EOD " + sessionIdentifier + " session:" +  e.getMessage(), e);
        }
    }

    public void setOutGoingSeqNo(String sessionIdentifier, int newSeqNo) throws IOException {
        Session ses = Session.lookupSession(getSessionID(sessionIdentifier));
        ses.setNextSenderMsgSeqNum(newSeqNo);
        logger.info("Reset Outgoing Sequence for Session: " + sessionIdentifier + ":" + newSeqNo);
    }

    public void setInComingSeqNo(String sessionIdentifier, int newSeqNo) throws IOException {
        Session ses = Session.lookupSession(getSessionID(sessionIdentifier));
        ses.setNextTargetMsgSeqNum(newSeqNo);
        logger.info("Reset Incoming Sequence for Session: " + sessionIdentifier + ":" + newSeqNo);
    }

    public void sendString(String fixBodyInit, String sessionIdentifier) throws InvalidMessage, SessionNotFound {
        SessionID sessionID = getSessionID(sessionIdentifier);
        if (sessionID != null) {
            DataDictionary dict;
            DataDictionary appDataDict;
            StringBuilder sb = new StringBuilder(fixBodyInit);
            if (fixBodyInit != null && !fixBodyInit.endsWith(IConstants.FD)) {
                sb.append(IConstants.FD);
            }
            String fixBody = sb.toString();
            dict = dictionaryMap.get(sessionIdentifier);

            Message message = new Message();
            if (sessionID.isFIXT()) {
                appDataDict = dictionaryMap.get("APP_" + sessionIdentifier);
                message.fromString(fixBody, dict, appDataDict, false);
            } else {
                message.fromString(fixBody, dict, false);
            }
            message.setString(FixConstants.FIX_TAG_IS_FROM_OMS, IConstants.SETTING_YES);

            final boolean isSendToTarget = getApplication().storeOpenOrdersWithHADetails(message);

            sendToTargetSession(sessionIdentifier, isSendToTarget, sessionID, message);
        } else {
            logger.error("Invalid Session Identifier: " + sessionIdentifier);
        }

    }

    private void sendToTargetSession(String sessionIdentifier, boolean isSendToTarget, SessionID sessionID, Message message) throws SessionNotFound {
        if (isSendToTarget){
            com.objectspace.jgl.Queue pendingMessages = pendingMessagesMap.get(sessionIdentifier);
            if (IConstants.STRING_SESSION_CONNECTED.equals(getSessionStatus(sessionID))) {
                Session.sendToTarget(message, sessionID);
                if (pendingMessages != null && !pendingMessages.isEmpty()) {
                    executorService.execute(new DisconnectedSessionMessageHandler(sessionIdentifier));
                }
            } else {
                logger.info("Session " + sessionIdentifier + " is not CONNECTED.");
                pendingMessages.push(message);
            }
        }
    }

    public String getSessionStatus(SessionID sessionID) {
        try {
            if (sessionID != null && Session.lookupSession(sessionID) != null) {
                if (Session.lookupSession(sessionID).isLoggedOn()) {
                    return IConstants.STRING_SESSION_CONNECTED;
                } else {
                    return IConstants.STRING_SESSION_DISCONNECTED;
                }
            } else {
                return IConstants.STRING_SESSION_DOWN;
            }
        } catch (Exception e) {
            logger.error("Error at getSessionStatus: " + e.getMessage(), e);
            return IConstants.STRING_SESSION_DOWN;
        }
    }

    public void updateSessionSeqInSecondary(String status) {    //<TDWL:25:30><NSDQ:25:30>
        SessionID sesID;
        String sessionIdentifier;
        int inSeq;
        int outSeq;

        StringTokenizer sessionToken = new StringTokenizer(status, "<");
        StringTokenizer infoToken;
        while (sessionToken.hasMoreTokens()) {
            sessionIdentifier = sessionToken.nextToken();

            infoToken = new StringTokenizer(sessionIdentifier, ":");
            sessionIdentifier = infoToken.nextToken().toUpperCase();
            inSeq = Integer.parseInt(infoToken.nextToken());
            outSeq = Integer.parseInt(infoToken.nextToken().replace(">", ""));

            sesID = sessionIDMap.get(sessionIdentifier);
            try {
                FileStore messageStore = (FileStore) storeFactoryMap.get(sesID).create(sesID);
                if (messageStore != null) {
                    if (inSeq == -1) {
                        inSeq = messageStore.getNextTargetMsgSeqNum();
                    }
                    if (outSeq == -1) {
                        outSeq = messageStore.getNextSenderMsgSeqNum();
                    }
                    messageStore.reset();
                    messageStore.setNextTargetMsgSeqNum(inSeq);
                    messageStore.setNextSenderMsgSeqNum(outSeq);
                    messageStore.close();
                    logger.debug("Updated sequence numbers in Secondary RTR : <" + sessionIdentifier + ":" + inSeq + ":" + outSeq + ">");
                } else {
                    logger.error("Session: " + sessionIdentifier + " is not configured");
                }
            } catch (Exception e) {
                logger.error("Error updating sequence nos in Secondary DFIX: " + e.getMessage(), e);
            }
        }
    }

    public String getSessionList() {
        StringBuilder sessionList = new StringBuilder();
        sessionList.append("SESSION_LIST").append(IConstants.DS);
        String address;
        String port;
        logger.info("Getting session list for admin");
        Iterator<SessionID> it = getSettings().keySet().iterator();

        while (it.hasNext()) {
            SessionID sessionID = it.next();
            try {
                String remoteAddress = Session.lookupSession(sessionID).getRemoteAddress();
                if (remoteAddress !=null) {
                    String[] remoteIpPort = remoteAddress.replaceFirst("^/+", "").split(":");
                    address = remoteIpPort[0];
                    port = remoteIpPort[1];
                } else {
                    if (IConstants.INITIATOR_CONNECTION_TYPE.equalsIgnoreCase(getSettings().get(sessionID).getString(sessionID, IConstants.SETTING_CONNECTION_TYPE))) {
                        address = getSettings().get(sessionID).getString(sessionID, IConstants.INITIATOR_ADDRESS);
                        port = getSettings().get(sessionID).getString(sessionID, IConstants.INITIATOR_PORT);
                    } else {
                        address = getSettings().get(sessionID).getString(sessionID, IConstants.ACCEPTOR_ADDRESS);
                        port = IConstants.ACCEPTOR_VIEW_PORT;
                    }
                }
                sessionList.append(sessionID.getBeginString()).append(IConstants.SS);
                sessionList.append(getSessionProperty(sessionID, IConstants.SESSION_IDENTIFIER)).append(IConstants.SS);
                sessionList.append(sessionID.getTargetCompID()).append(IConstants.SS);
                sessionList.append(sessionID.getSenderCompID()).append(IConstants.SS);
                sessionList.append(address).append(IConstants.SS);
                sessionList.append(port).append(IConstants.SS);
                sessionList.append(getSessionStatus(sessionID));
                sessionList.append(IConstants.DS);
            } catch (Exception e) {
                logger.error("Error Getting session list for admin: " + e.getMessage(), e);
            }
        }
        return sessionList.toString();
    }

    public String getAllSessionProperty(SessionID sessionID, String propKey) {
        String propVal = null;
        try {
            if (getSettings().get(sessionID) != null) {
                propVal = getSettings().get(sessionID).getSessionProperties(sessionID, false).getProperty(propKey);
            } else {
                propVal = getSimSettings().getSessionProperties(sessionID, false).getProperty(propKey);

            }
        } catch (ConfigError configError) {
            logger.error("Error at getAllSessionProperty: " + configError.getMessage(), configError);
        }
        return propVal;
    }

    public static SessionSettings getSimSettings() {
        return simSettings;
    }

    public static void setSimSettings(SessionSettings simSettings) {
        FIXClient.simSettings = simSettings;
    }

    public void startSimulator() {
        if (!Settings.isSimulatorOn()) {
            return;
        }
        logger.debug("Starting FIX FIXSimApplication....");
        try (FileInputStream in = new FileInputStream(Settings.getSimCfgFileName())) {
            startSimulator(in);
        } catch (Exception e) {
            logger.error("Error Starting FIX FIXSimApplication: " + e.getMessage(), e);
        }
    }

    private void startSimulator(FileInputStream in) {
        try {
            setSimSettings(getSessionSettings(in));
            fixSimApplication = new FIXSimApplication(simSettings);
            FileStoreFactory fileStoreFactory = new FileStoreFactory(simSettings);
            LogFactory logFactory = getFileLogFactory(simSettings);
            MessageFactory messageFactory = new DefaultMessageFactory();
            startAcceptorSimSessions(fileStoreFactory, logFactory, messageFactory);
            startInitiatorSimSessions(fileStoreFactory, logFactory, messageFactory);
            loadSimulatorCache();
        } catch (ConfigError ce) {
            logger.error("Error starting simulator: " + ce.getMessage(), ce);
        }
    }

    private void startInitiatorSimSessions(FileStoreFactory fileStoreFactory, LogFactory logFactory, MessageFactory messageFactory) {
        try {
            logger.info("Configuring initiators sessions");
            simInitiator = new ThreadedSocketInitiator(fixSimApplication, fileStoreFactory
                    , simSettings, logFactory, messageFactory);
            simInitiator.start();
        } catch (Exception configError) {
            logger.debug("No Initiators available for Simulator",configError);
        }
    }

    private void startAcceptorSimSessions(FileStoreFactory fileStoreFactory, LogFactory logFactory, MessageFactory messageFactory) {
        try {
            logger.info("Configuring acceptor sessions");
            simAcceptor = new ThreadedSocketAcceptor(fixSimApplication, fileStoreFactory
                    , simSettings, logFactory, messageFactory);
            simAcceptor.start();
        } catch (Exception configError) {
            logger.debug("No Acceptors available for Simulator",configError);
        }
    }

    private void loadSimulatorCache() throws ConfigError {
        logger.info("Creating Session FileStore:");
        Iterator<SessionID> it = getSimSettings().sectionIterator();

        while (it.hasNext()) {
            SessionID sessionID = it.next();
            String sessionIdentifier = getSimulatorProperty(sessionID, IConstants.SESSION_IDENTIFIER);
            if (sessionIdentifier.length() > longestLengthSessionIdentifier) {
                longestLengthSessionIdentifier = sessionIdentifier.length();
            }
            String senderCompId = sessionID.getSenderCompID();
            if (senderCompId.length() > longestLengthSenderCompId) {
                longestLengthSenderCompId = senderCompId.length();
            }
            String targetCompId = sessionID.getTargetCompID();
            if (targetCompId.length() > longestLengthTargetCompId) {
                longestLengthTargetCompId = targetCompId.length();
            }
            sessionIDMap.put(sessionIdentifier, sessionID);
            DataDictionary dict;
            if (sessionID.isFIXT()) {
                dict = getDataDictionary(getSimulatorProperty(sessionID, IConstants.SETTING_TRANSPORT_DATA_DICTIONARY));
                DataDictionary appDataDictionary = new DataDictionary(getSimulatorProperty(sessionID, IConstants.SETTING_APP_DATA_DICTIONARY));
                dictionaryMap.put(getSimulatorProperty(sessionID, IConstants.SESSION_IDENTIFIER), dict);
                dictionaryMap.put("APP_" + getSimulatorProperty(sessionID, IConstants.SESSION_IDENTIFIER), appDataDictionary);
            } else {
                dict = getDataDictionary(getSimulatorProperty(sessionID, IConstants.SETTING_DATA_DICTIONARY));
                dictionaryMap.put(getSimulatorProperty(sessionID, IConstants.SESSION_IDENTIFIER), dict);
            }
        }
    }

    public String getSimulatorProperty(SessionID sessionID, String propKey) {
        try {
            return getSimSettings().getSessionProperties(sessionID, true).getProperty(propKey);
        } catch (ConfigError configError) {
            logger.error("Error loading sim property:" + propKey + ":" + configError.getMessage(), configError);
            return null;
        }
    }

    public FIXApplication getApplication() {
        return application;
    }

    public Map<SessionID, FlowController> getFlowControllers() {
        return flowControllers;
    }

    public List<Message> getMessages(SessionID sessionID, int startSeq, int endSeq) throws IOException, InvalidMessage {
        DataDictionary appDataDict;
        String sessionIdentifier = getSessionProperty(sessionID, IConstants.SESSION_IDENTIFIER);
        DataDictionary dict = dictionaryMap.get(sessionIdentifier);
        ArrayList<String> strMessages = new ArrayList<>();
        ArrayList<Message> messages = new ArrayList<>();
        Session ses = Session.lookupSession(sessionID);
        ses.getStore().get(startSeq, endSeq, strMessages);
        for (String strMessage :
                strMessages) {
            Message message = new Message();
            if (sessionID.isFIXT()) {
                appDataDict = dictionaryMap.get("APP_" + sessionIdentifier);
                message.fromString(strMessage, dict, appDataDict, false);
            } else {
                message.fromString(strMessage, dict, false);
            }
            messages.add(message);
        }
        return messages;
    }

    public boolean storeMessage(SessionID sessionID, int seqNo, String strMessage) throws IOException {
        Session ses = Session.lookupSession(sessionID);
        if (!strMessage.endsWith(IConstants.FD)) {
            strMessage += IConstants.FD;
        }
        return ses.getStore().set(seqNo, strMessage);
    }

    public int getLongestLengthSessionIdentifier() {
        return longestLengthSessionIdentifier;
    }

    public int getLongestLengthTargetCompId() {
        return longestLengthTargetCompId;
    }

    public int getLongestLengthSenderCompId() {
        return longestLengthSenderCompId;
    }

    public com.objectspace.jgl.Queue getPendingMessages(String sessionIdentifier) {
        synchronized (pendingMessagesMap.get(sessionIdentifier)){
            return pendingMessagesMap.get(sessionIdentifier);
        }
    }

    public ScheduledExecutorService getFlowControlPool() {
        return flowControlPool;
    }

    public void stopFIXSimulator() {
        logger.debug("Stop Simulator Sessions");
        simAcceptor.stop();
        simInitiator.stop();
        fixSimApplication.stopOrderHandler();
        fixSimApplication.stopNegDealExecutor();
    }

    public Set<SessionID> getManuallyLoggedOutAcceptors() {
        return manuallyLoggedOutAcceptors;
    }

    public List<String> reload() {
        logger.debug("Reload FIX Client.");
        ArrayList<String> disconnectedSessions = new ArrayList<>();
        ArrayList<String> reloadedSessions = new ArrayList<>();
        try {
            for (SessionID initiator :
                    initiators.keySet()) {
                if (!IConstants.STRING_SESSION_CONNECTED.equals(getSessionStatus(initiator))){
                    disconnectedSessions.add(getSessionProperty(initiator, IConstants.SESSION_IDENTIFIER));
                }
            }
            for (SessionID acceptor :
                    acceptors.keySet()) {
                if (!IConstants.STRING_SESSION_CONNECTED.equals(getSessionStatus(acceptor))){
                    disconnectedSessions.add(getSessionProperty(acceptor, IConstants.SESSION_IDENTIFIER));
                }
            }
            for (String sessionIdentifier :
                    disconnectedSessions) {
                if (reload(sessionIdentifier)){
                    reloadedSessions.add(sessionIdentifier);
                }
            }
        } catch (Exception e) {
            logger.error("Error at Reload FIX Client: " + e.getMessage(), e);
        }
        return reloadedSessions;
    }

    public boolean reload(String sessionIdentifier) throws ConfigError, IOException {
        boolean status = false;
        SessionID sessionID = sessionIDMap.get(sessionIdentifier);
        Connector connector = null;
        if (!IConstants.STRING_SESSION_CONNECTED.equals(getSessionStatus(sessionID))) {
            if (initiators.containsKey(sessionID)){
                connector = initiators.get(sessionID);
            }
            if (acceptors.containsKey(sessionID)){
                connector = acceptors.get(sessionID);
                manuallyLoggedOutAcceptors.remove(sessionID);
            }
        }
        if (connector != null) {
            connector.stop();
            try (FileInputStream in = new FileInputStream(Settings.getCfgFileName())) {
                SessionSettings fullSessionSettings = getSessionSettings(in);
                SessionSettings sessionSettings = new SessionSettings();
                sessionSettings.set(sessionID, new Dictionary(sessionIdentifier, fullSessionSettings.getSessionProperties(sessionID, true)));
                settings.put(sessionID, sessionSettings);
                connector = getConnector(sessionIdentifier, sessionSettings, sessionID);
                connector.start();
                waitUntilConnect(Session.lookupSession(sessionID));
                status = true;
            }
        }
        return status;
    }

    Connector getConnector(String sessionIdentifier, SessionSettings sessionSettings, SessionID sessionID) throws ConfigError {
        Connector connector;
        if (IConstants.INITIATOR_CONNECTION_TYPE.equalsIgnoreCase(sessionSettings.getString(sessionID, IConstants.SETTING_CONNECTION_TYPE))) {
            logger.info("Reloading initiator sessions: " + sessionIdentifier);
            connector = getConnector(sessionSettings, sessionID, IConstants.INITIATOR_CONNECTION_TYPE);
            initiators.put(sessionID, (ThreadedSocketInitiator) connector);
        } else {
            logger.info("Reloading acceptor sessions: " + sessionIdentifier);
            connector = getConnector(sessionSettings, sessionID, IConstants.ACCEPTOR_CONNECTION_TYPE);
            acceptors.put(sessionID, (ThreadedSocketAcceptor) connector);
        }
        return connector;
    }

    Connector getConnector(SessionSettings sessionSettings, SessionID sessionID, String connectionType) throws ConfigError {
        if (IConstants.INITIATOR_CONNECTION_TYPE.equalsIgnoreCase(connectionType)) {
            return new ThreadedSocketInitiator(application, storeFactoryMap.get(sessionID), sessionSettings, logFactoryMap.get(sessionID), messageFactoryMap.get(sessionID));
        } else { //IConstants.ACCEPTOR_CONNECTION_TYPE
            return new ThreadedSocketAcceptor(application, storeFactoryMap.get(sessionID), sessionSettings, logFactoryMap.get(sessionID), messageFactoryMap.get(sessionID));
        }
    }

    public Map<String, DataDictionary> getDictionaryMap() {
        return dictionaryMap;
    }

    ThreadedSocketInitiator getSimInitiator() {
        return simInitiator;
    }

    ThreadedSocketAcceptor getSimAcceptor() {
        return simAcceptor;
    }
    HashMap<String, com.objectspace.jgl.Queue> getPendingMessagesMap(){
        return pendingMessagesMap;
    }
}
