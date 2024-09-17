package com.mubasher.oms.dfixrouter.server.exchange;

/**
 * Created by IntelliJ IDEA.
 * User: chamindah
 * Date: Oct 26, 2007
 * Time: 1:59:51 PM
 * To change this template use File | Settings | File Templates.
 */

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.sender.MessageSender;
import com.mubasher.oms.dfixrouter.server.oms.JMSSender;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareSender;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.WatchDogHandler;
import com.objectspace.jgl.Queue;
import quickfix.field.MsgType;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * this class hold the message recieve from exchange before send to OMS queue
 */

public class FromExchangeQueue implements Runnable {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.FromExchangeQueue");
    private final Queue queue;
    private final int[] intermediateQueueCounters;//Holds the Next Intermediate QueueId for the Customer.
    private final int[] simIntermediateQueueCounters;//Holds the Next available QueueId for Simultaneous processing.
    private final int[] simIcmDropCopyQueueCounters;//Holds the Next available QueueId for Simultaneous processing of ICM Drop Copy.
    private final int[] intermediateQueueCounts;//Holds the IntermediateQueue counts against the ServerId(Array position)
    private final int[] icmDropCopyQueueCounts;//Holds the ICM DropCopy Queue counts against the ServerId(Array position)
    private final List<Map<String, Integer>> intermediateQueueData;//Holds the Intermediate queue Map(CustomerId
    // (MubasherNo) Vs IntermediateQueueId) against the ServerId(List Position)
    private final int senderCount;
    private boolean watchdogEnabled;
    private List<Map<String, MiddlewareSender>> senders;
    private ExecutorService executorService;
    private Map<Integer, List<DFIXMessage>> resendMsgList;  //HostId, DFIXMessage list

    public FromExchangeQueue() {
        super();
        queue = new Queue();
        senderCount = Settings.getInt(SettingsConstants.HOSTS_COUNT);
        watchdogEnabled = (IConstants.SETTING_YES).equalsIgnoreCase(Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG));
        senders = new ArrayList<>();
        intermediateQueueCounters = new int[senderCount];
        simIntermediateQueueCounters = new int[senderCount];
        simIcmDropCopyQueueCounters = new int[senderCount];
        intermediateQueueCounts = new int[senderCount];
        icmDropCopyQueueCounts = new int[senderCount];
        intermediateQueueData = new ArrayList<>();
        resendMsgList = new HashMap<>();
    }

    public void addMsg(DFIXMessage msg) {
        int servedById = msg.getServedBy();
        if (servedById <= 0) {
            sendMessageToAll(msg);
        } else {
            sendMessageToSingleServer(msg, servedById - 1);
        }
    }

    @Override
    public void run() {
        try {
            if (!queue.isEmpty()) {
                DFIXMessage msg = (DFIXMessage) queue.front();
                if (msg.getServedBy() <= 0) {
                    sendMessageToAll(msg);
                } else {
                    sendMessageToSingleServer(msg, msg.getServedBy() - 1);
                }
                queue.pop();
            }
        } catch (Exception e) {
            logger.error("Exception at FromExchangeQueue Thread: " + e.getMessage(), e);
        }
    }

    public void sendMessageToAll(DFIXMessage msg) {
        for (int i = 0; i < senderCount; i++) {
            sendMessageToSingleServer(msg, i);
            logger.debug("Sent Message to Middleware Sender= " + i + " " + msg.toString());
        }
    }

    public void sendMessageToSingleServer(DFIXMessage msg, int serverId) {
        if (serverId >= senderCount) {
            serverId = 0;
        }
        if(!this.isValidMessageType(serverId, msg)){
            return;
        }
        final String key = determineQueueKey(msg, serverId);
        MiddlewareSender middlewareSender = senders.get(serverId).get(key);
        if (middlewareSender.isConnected()) {
            logger.debug("Sent Message to Middleware Sender= " + msg.toString());
            middlewareSender.getQueue().push(msg);
            if (!middlewareSender.isRunning()) {
                middlewareSender.setRunning(true);
                executorService.execute(middlewareSender);
            }
        } else if (watchdogEnabled && senderCount > 1) {
            storeMessageToSendLater(msg, serverId + 1);
        } else if (senderCount > 1) {
            logger.error("Wrong server id/Server is disconnected: " + serverId);
            int oldServedById = serverId++;
            if (serverId == (msg.getServedBy() - 1)) {
                storeMessageToSendLater(msg, middlewareSender);
                return;
            } else {
                if (serverId == intermediateQueueData.size()) {
                    serverId = 0;
                }
                intermediateQueueData.get(serverId).putAll(intermediateQueueData.get(oldServedById));
            }
            sendMessageToSingleServer(msg, serverId);
        } else {    //Sender count = 1
            storeMessageToSendLater(msg, middlewareSender);
        }
    }

    private String determineQueueKey(DFIXMessage msg, int serverId) {
        String key = MessageSender.PRIMARY_QUEUE_KEY;
        if (msg.getFixMsgType() != null
                && (msg.getFixMsgType().charAt(0) == FixConstants.FIX_VALUE_35_USER_DEFINED
                || msg.getFixMsgType().equals(MsgType.TRADING_SESSION_STATUS)
                || msg.getFixMsgType().equals(MsgType.TRADE_CAPTURE_REPORT)
                || msg.getFixMsgType().equals(MsgType.TRADE_CAPTURE_REPORT_ACK))) {
            String interKey = MessageSender.U_MESSAGE_QUEUE_KEY;
            if (senders.get(serverId).keySet().contains(interKey)) {
                key = interKey;
            }
            if (FixConstants.FIX_VALUE_35_VIEW_ACCOUNT_RESPONSE.equalsIgnoreCase(msg.getFixMsgType())) {
                //From Customized quickfixj-all-2.0.0_DFN_2.jar.
                msg.setMessage(msg.getMessage().replaceAll(String.valueOf(FixConstants.FIX_TAG_100000_DUMMY_COUNT), String.valueOf(FixConstants.FIX_TAG_9710_COUNT)));
            }
        } else if (msg.getFixTag10008() > 0 || msg.isSimProcessing()) {
            setIntermediateQueueId(msg, serverId);
            String interKey = MessageSender.getIntermediateQueueKey(msg.getInterQueueId());
            if (senders.get(serverId).keySet().contains(interKey)) {
                key = interKey;
            }
        }
        return key;
    }

    public void storeMessageToSendLater(DFIXMessage msg, int serverId) {
        if (msg.getServedBy() > 0) {    //Store only the messages assigned with a route id, the host id is returned here
            synchronized (this) {
                if (resendMsgList.get(msg.getServedBy()) == null) {
                    resendMsgList.put(msg.getServedBy(), new ArrayList<>());
                }
            }
            resendMsgList.get(msg.getServedBy()).add(msg);
            logger.error("Server is disconnected:HOST_ID" + serverId + " msg saved to send later: " + msg.getMessage());
        }
    }

    public synchronized void resendStoredMessages(int hostId) {
        logger.info("Resend Stored messages: HOST_ID:" + hostId);
        if (resendMsgList.get(hostId) != null) {
            List<DFIXMessage> resendMsgListHost = resendMsgList.remove(hostId);
            for (DFIXMessage dfixMessage: resendMsgListHost) {
                int servedById = WatchDogHandler.getAppServerId(dfixMessage.getMessage());  //Find the route id to this message again, because it may have been changed
                logger.info("Resend Stored messages: new HOST_ID:" + servedById + " msg: " + dfixMessage.toString());
                dfixMessage.setServedBy(servedById);
                addMsg(dfixMessage);
            }
        }
    }

    private void storeMessageToSendLater(DFIXMessage msg, MiddlewareSender middlewareSender) {
        middlewareSender.getQueue().push(msg);
        logger.error("No Connected servers available, Queued in cache to send Message later: "
                + msg.toString()
                + " at : " + middlewareSender.getQueue().size());
    }

    public void setSenders(List<Map<String, MiddlewareSender>> senders) {
        this.senders = senders;
    }

    public void startSenderQueueConnection() throws DFIXConfigException {
        int x = 0;
        int i;
        for (Host host : Settings.getHostList()) {
            i = host.getId() - 1;
            senders.add(MessageSender.getQSender(host));
            intermediateQueueCounters[i] = 0;
            simIntermediateQueueCounters[i] = 0;
            simIcmDropCopyQueueCounters[i] = host.getIntermediateQueueCount();
            HashMap<String, Integer> intermediateQueueIds = new HashMap<>();
            intermediateQueueData.add(intermediateQueueIds);
            intermediateQueueCounts[i] = host.getIntermediateQueueCount();
            icmDropCopyQueueCounts[i] = host.getIcmDropCopyQueueCount();
            MessageSender.startSender(senders.get(i).values());
            x += senders.get(i).size();
            logger.debug("Starting Middleware Sender: " + host.getIp());
        }
        if (x > 0) {
            executorService = Executors.newFixedThreadPool(x);
        }
    }

    public void stopSenderQueueConnection() {
        for (Map<String, MiddlewareSender> senderHashMap : senders) {
            for (MiddlewareSender sen : senderHashMap.values()) {
                try {
                    logger.info("Stop Sender Queue Connection: " + sen.toString());
                    sen.close();
                } catch (Exception e) {
                    logger.error("Error at Stop Sender Queue Connection: " + e.getMessage(), e);
                }
            }
        }
        if (executorService != null && !executorService.isShutdown()) {
            logger.info("Pool Shutdown : " + executorService.toString());
            executorService.shutdown();
        }
    }

    public void startSenderQueueConnection(Host host) throws DFIXConfigException {
        int i = host.getId() - 1;
        senders.add(MessageSender.getQSender(host));
        intermediateQueueCounters[i] = 0;
        simIntermediateQueueCounters[i] = 0;
        HashMap<String, Integer> intermediateQueueIds = new HashMap<>();
        intermediateQueueData.add(intermediateQueueIds);
        intermediateQueueCounts[i] = host.getIntermediateQueueCount();
        MessageSender.startSender(senders.get(i).values());
        logger.debug("Starting Middleware Sender: " + host.getIp());
        if (!Settings.getHostList().isEmpty()) {
            executorService = Executors.newFixedThreadPool(Settings.getHostList().size());
        }
    }

    public void stopSenderQueueConnection(int omsId) {
        for (Map<String, MiddlewareSender> senderHashMap : senders) {
            for (MiddlewareSender sen : senderHashMap.values()) {
                try {
                    if (sen instanceof JMSSender
                            && ((JMSSender) sen).getHostId().equals(IConstants.HOST_MEMBER_PREFIX + "-" + omsId)) {
                        logger.info("Stop Sender Queue Connection: " + sen.toString());
                        sen.close();
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Error at Stop Sender Queue Connection for omsId: " + omsId + ":" + e.getMessage(), e);
                }
            }
        }
        /*Remove the host from the senders list*/
        try {
            senders.remove(Settings.getHostIdMap().get(omsId) - 1);
        } catch (Exception e) {
            logger.error("Failed to remove the host from senders list", e);
        }
    }

    private void setIntermediateQueueId(DFIXMessage msg, int serverId) {
        String fixTag10008 = String.valueOf(msg.getFixTag10008());
        int intermediateQueueId = 0;
        if (msg.getInterQueueId() > -1) {
            intermediateQueueId = msg.getInterQueueId();
            logger.debug("Setting ICM ORD From Exchange Queue:"+intermediateQueueId);
        } else if (msg.isSimProcessing()) {
            if (simIntermediateQueueCounters[serverId] == intermediateQueueCounts[serverId]) {
                simIntermediateQueueCounters[serverId] = 0;
            }
            intermediateQueueId = simIntermediateQueueCounters[serverId]++;
        } else if (msg.getFixTag10008() > 0) {
            intermediateQueueId = getIntermediateQueueId(fixTag10008, serverId);
        }
        msg.setInterQueueId(intermediateQueueId);
        String message = msg.getMessage();
        StringBuilder sb = new StringBuilder(message);
        if (!message.endsWith(IConstants.FD)) {
            sb.append(IConstants.FD);
        }
        sb.append(FixConstants.FIX_TAG_INTER_QUEUE_ID).append("=").append(intermediateQueueId).append(IConstants.FD);
        msg.setMessage(sb.toString());
    }

    private synchronized int getIntermediateQueueId(String fixTag10008, int serverId) {
        int intermediateQueueId;
        if (intermediateQueueData.get(serverId).keySet().contains(fixTag10008)) {
            intermediateQueueId = intermediateQueueData.get(serverId).get(fixTag10008);
        } else {
            if (intermediateQueueCounters[serverId] == intermediateQueueCounts[serverId]) {
                intermediateQueueCounters[serverId] = 0;
            }
            intermediateQueueId = intermediateQueueCounters[serverId]++;
            intermediateQueueData.get(serverId).put(fixTag10008, intermediateQueueId);
        }
        return intermediateQueueId;
    }

    public void clearCache() {
        logger.info("Cache Clear: " + new Date());
        for (Map<String, Integer> map :
                intermediateQueueData) {
            map.clear();
        }
    }

    private boolean isValidMessageType(int hostId, DFIXMessage dfixMessage) {
        Host host = Settings.getHostList().get(hostId);
        if (host.getSupportedMessageTypes() == null || host.getSupportedMessageTypes().isEmpty()) {
            return true;
        } else {
            return host.getSupportedMessageTypes().contains(dfixMessage.getFixMsgType());
        }
    }

    public synchronized int getIntermediateQueueId(int serverId) {
        if (simIcmDropCopyQueueCounters[serverId] == (intermediateQueueCounts[serverId] + icmDropCopyQueueCounts[serverId])) {
            simIcmDropCopyQueueCounters[serverId] = intermediateQueueCounts[serverId];
        }
        return simIcmDropCopyQueueCounters[serverId]++;
    }
}
