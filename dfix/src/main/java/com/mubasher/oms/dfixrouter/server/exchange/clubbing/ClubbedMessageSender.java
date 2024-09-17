package com.mubasher.oms.dfixrouter.server.exchange.clubbing;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.sender.MessageSender;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareSender;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.objectspace.jgl.Queue;

import java.util.List;

/**
 * this class is used to send messages to the HOSTn_CLUBBED_QUEUE
 */
public class ClubbedMessageSender extends Thread {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.clubbing.ClubbedMessageSender");

    private MiddlewareSender[] senders;
    private Queue queue;
    private boolean isStarted;
    private boolean isActive;

    public ClubbedMessageSender() {
        super();
        queue = new Queue();
        isStarted = false;
        isActive = false;
    }

    @Override
    public void run() {
        do {
            if (!isStarted) {
                logger.info("ClubbedMessageSender invoked");
                try {
                    isStarted = startSenderQueueConnection();
                } catch (Exception e) {
                    logger.error("ClubbedMessageSender invoke Failed: " + e.getMessage(), e);
                }
            }
            if (queue.size() > 0) {
                DFIXMessage dfixMessage = (DFIXMessage) queue.pop();
                if (dfixMessage.getServedBy() <= 0) {
                    sendMessageToAll(dfixMessage);
                } else {
                    sendMessageToSingleServer(dfixMessage, dfixMessage.getServedBy() - 1);
                }
            } else {
                DFIXRouterManager.sleepThread(10);
            }
        } while (isActive);
    }

    private boolean startSenderQueueConnection() throws DFIXConfigException {
        boolean result = false;
        senders = new MiddlewareSender[Settings.getHostList().size()];
        for (Host host : Settings.getHostList()) {
            int i = host.getId() - 1;
            if (host.getClubQName() != null && !host.getClubQName().isEmpty()) {
                result = true;
                isActive = true;
                senders[i] = MessageSender.getQSender(host).get(MessageSender.CLUBB_QUEUE_NAME);
                logger.debug("Starting Middleware Club Message Sender: " + host.getIp());
            }
        }
        return result;
    }

    private void sendMessageToAll(DFIXMessage dfixMessage) {
        for (MiddlewareSender sender :
                senders) {
            sender.getQueue().add(dfixMessage);
        }
    }

    private void sendMessageToSingleServer(DFIXMessage dfixMessage, int serverId) {
        MiddlewareSender middlewareSender = senders[serverId];
        logger.debug("Sent Message to Middleware Sender= " + dfixMessage.toString());
        middlewareSender.getQueue().add(dfixMessage);
    }

    public void addMessages(List<DFIXMessage> dfixMessageList) {
        for (DFIXMessage dfixMessage :
                dfixMessageList) {
            queue.push(dfixMessage);
        }
    }
}
