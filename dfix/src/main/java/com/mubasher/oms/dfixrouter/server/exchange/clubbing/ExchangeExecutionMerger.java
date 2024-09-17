package com.mubasher.oms.dfixrouter.server.exchange.clubbing;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by nuwanis on 2/20/2018.
 */
public class ExchangeExecutionMerger extends Thread {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.clubbing.ExchangeExecutionMerger");

    private List<String> clOrdIdList;
    private Map<String, Message> clubbedMsgsMap;
    private Map<String, List<Message>> previousMsgsMap;
    private ClubbedMessageSender clubbedMessageSender;
    private ScheduledExecutorService executor;
    private com.objectspace.jgl.Queue queue;
    private Object lock;
    private long clubbedMsgDelay = 600000L;

    public ExchangeExecutionMerger() {
        lock = new Object();
        clOrdIdList = new ArrayList<>();
        clubbedMsgsMap = new ConcurrentHashMap<>();
        previousMsgsMap = new HashMap<>();
        queue = new com.objectspace.jgl.Queue();
        clubbedMessageSender = new ClubbedMessageSender();
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void init() {
        if (Settings.getString(SettingsConstants.CLUBBED_MSG_DELAY) != null) {
            clubbedMsgDelay = Long.parseLong(Settings.getString(SettingsConstants.CLUBBED_MSG_DELAY));
        }
        clubbedMessageSender.start();
    }

    public void addMsg(Message message, String sessionIdentifier) {
        message.setString(FixConstants.FIX_TAG_SES_IDENTIFIER, sessionIdentifier);
        queue.push(message);
    }

    @Override
    public void run() {
        Message message = null;
        while (DFIXRouterManager.getInstance().isStarted()) {
            try {
                if (queue.size() > 0) {
                    message = (Message) queue.pop();
                    processExecutionReport(message);
                } else {
                    DFIXRouterManager.sleepThread(1000);
                }
            } catch (Exception ex) {
                logger.error("Error in processing execution report for clubbing :", ex);
                if (message != null) {
                    sendExecutionReportAtException(message);
                }
            }
        }
    }

    public void processExecutionReport(Message message) throws FieldNotFound {
        String clOrdId = message.getString(ClOrdID.FIELD);
        Message clubbedMessage;
        if (message.isSetField(LastShares.FIELD)
                && message.isSetField(LastPx.FIELD)
                && message.isSetField(OrdStatus.FIELD)
                && message.getInt(LastShares.FIELD) > 0
                && message.getDouble(LastPx.FIELD) > 0
                && message.getChar(OrdStatus.FIELD) != OrdStatus.FILLED) {
            synchronized (lock) {
                if (clubbedMsgsMap.containsKey(clOrdId)) {
                    clubbedMessage = clubbedMsgsMap.get(clOrdId);
                    if (clubbedMessage.getDouble(LastPx.FIELD) == message.getDouble(LastPx.FIELD)) {        //lastPrice of the currentMsg == lastPrice of the previousMsg
                        clubAndStoreExecutionReport(clOrdId, clubbedMessage, message);
                    } else {                //lastPrice of the currentMsg != lastPrice of the previousMsg
                        sendClubbedExecutionReport(clOrdId);
                        storeExecutionReport(clOrdId, message);
                    }
                } else {                    //New execution report for the clOrdId - no previous messages
                    storeExecutionReport(clOrdId, message);
                }
            }
        } else {        //Execution reports such as queued, filled, replaced, cancelled etc
            synchronized (lock) {
                if (clubbedMsgsMap.containsKey(clOrdId)) {
                    sendClubbedExecutionReport(clOrdId);
                } else if (message.isSetField(OrigClOrdID.FIELD)
                        && clubbedMsgsMap.containsKey(message.getString(OrigClOrdID.FIELD))) {
                    sendClubbedExecutionReport(message.getString(OrigClOrdID.FIELD));
                }
                sendExecutionReport(message);
            }
        }
    }

    public void sendExecutionReportAtException(Message message) {
        String sesIdentifier = "";
        try {
            sesIdentifier = message.getString(FixConstants.FIX_TAG_SES_IDENTIFIER);
            message.removeField(FixConstants.FIX_TAG_SES_IDENTIFIER);
            sendDfixMessage(message, sesIdentifier);
        } catch (FieldNotFound ex) {
            logger.error("Error at finding sessionIdentifier for clubbing execution", ex);
        }
    }

    public void clubAndStoreExecutionReport(String clOrdId, Message clubbedMsg, Message currentMsg) throws FieldNotFound {
        Message newClubbedMsg = (Message) currentMsg.clone();
        int cumLastShares = currentMsg.getInt(LastShares.FIELD) + clubbedMsg.getInt(LastShares.FIELD);        //the synchronization is guranteed by the message flow
        previousMsgsMap.computeIfAbsent(clOrdId, k -> new ArrayList<>());

        previousMsgsMap.get(clOrdId).add(currentMsg);
        newClubbedMsg.setInt(LastShares.FIELD, cumLastShares);       //When the quantity changes always clone the message
        clubbedMsgsMap.put(clOrdId, newClubbedMsg);
        logger.debug("Clubbed execution report lastShares:" + cumLastShares + ",execid:" + newClubbedMsg.getString(ExecID.FIELD));
    }

    public void sendClubbedExecutionReport(String clOrdId) throws FieldNotFound {
        Message clubbedMessage = clubbedMsgsMap.remove(clOrdId);
        List<Message> preMsgList = previousMsgsMap.remove(clOrdId);
        String executionId = clubbedMessage.getString(ExecID.FIELD);
        String sessionIdentifier = clubbedMessage.getString(FixConstants.FIX_TAG_SES_IDENTIFIER);
        clubbedMessage.removeField(FixConstants.FIX_TAG_SES_IDENTIFIER);
        sendDfixMessage(clubbedMessage, sessionIdentifier);
        sendPreviousMsgList(preMsgList, executionId, sessionIdentifier);
        logger.debug("Clubbed Execution sent with: " + executionId);
    }

    public void storeExecutionReport(String clOrdId, Message message) {
        clubbedMsgsMap.put(clOrdId, message);

        List<Message> previousMsgsList = new ArrayList<>();
        previousMsgsList.add(message);
        previousMsgsMap.put(clOrdId, previousMsgsList);
        logger.debug("Execution report stored for clubbing:" + message.toString());
    }

    public void sendExecutionReport(Message message) throws FieldNotFound {
        String sesIdentifier = message.getString(FixConstants.FIX_TAG_SES_IDENTIFIER);
        message.removeField(FixConstants.FIX_TAG_SES_IDENTIFIER);
        sendDfixMessage(message, sesIdentifier);
    }

    public void sendDfixMessage(Message message, String sesIdentifier) throws FieldNotFound {
        DFIXMessage dfixMessage = new DFIXMessage();
        dfixMessage.setMessage(message.toString());
        dfixMessage.setExchange(sesIdentifier);
        dfixMessage.setSequence(Long.toString(System.currentTimeMillis()));
        dfixMessage.setType(Integer.toString(IConstants.APPLICATION_MESSAGE_RECEIVED));
        FIXClient.getFIXClient().getApplication().populateIntermediateQueueData(message, dfixMessage);
        FIXClient.getFIXClient().getApplication().populateHAData(message, dfixMessage);
        if (DFIXRouterManager.getFromExchangeQueue() != null) {
            DFIXRouterManager.getFromExchangeQueue().addMsg(dfixMessage);
        }
    }

    public void sendPreviousMsgList(List<Message> preMsgList, String executionId, String sesIdentifier) throws FieldNotFound {
        preMsgList.remove(preMsgList.size() - 1);       //remove the last item
        List<DFIXMessage> dfixMsgList = new ArrayList<>();
        for (Message preMessage : preMsgList) {
            preMessage.setString(FixConstants.FIX_TAG_CLUBBED_EXEC_ID, executionId);
            preMessage.removeField(FixConstants.FIX_TAG_SES_IDENTIFIER);
            DFIXMessage dfixMessage = new DFIXMessage();
            dfixMessage.setMessage(preMessage.toString());
            dfixMessage.setExchange(sesIdentifier);
            dfixMessage.setSequence(Long.toString(System.currentTimeMillis()));
            dfixMessage.setType(Integer.toString(IConstants.APPLICATION_MESSAGE_RECEIVED));
            FIXClient.getFIXClient().getApplication().populateIntermediateQueueData(preMessage, dfixMessage);
            FIXClient.getFIXClient().getApplication().populateHAData(preMessage,dfixMessage);
            dfixMsgList.add(dfixMessage);
        }

        if (!dfixMsgList.isEmpty()) {
            Runnable task = () -> sendClubbedMessages(dfixMsgList);
            executor.schedule(task, clubbedMsgDelay, TimeUnit.MILLISECONDS);
        }
    }

    private void sendClubbedMessages(List<DFIXMessage> dfixMessageList) {
        clubbedMessageSender.addMessages(dfixMessageList);
    }

    public void timeOut() throws FieldNotFound {
        synchronized (lock) {
            for (String clOrdid : clubbedMsgsMap.keySet()) {
                sendClubbedExecutionReport(clOrdid);
            }
        }
    }

    public List<String> getClOrdIdList() {
        return clOrdIdList;
    }

    public Map<String, Message> getClubbedMsgsMap() {
        return clubbedMsgsMap;
    }

    public void setClubbedMsgsMap(Map<String, Message> clubbedMsgsMap) {
        this.clubbedMsgsMap = clubbedMsgsMap;
    }

    public Map<String, List<Message>> getPreviousMsgsMap() {
        return previousMsgsMap;
    }

    public void setPreviousMsgsMap(Map<String, List<Message>> previousMsgsMap) {
        this.previousMsgsMap = previousMsgsMap;
    }

    public ClubbedMessageSender getClubbedMessageSender() {
        return clubbedMessageSender;
    }

    public void setClubbedMessageSender(ClubbedMessageSender clubbedMessageSender) {
        this.clubbedMessageSender = clubbedMessageSender;
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    public void setExecutor(ScheduledExecutorService executor) {
        this.executor = executor;
    }
}
