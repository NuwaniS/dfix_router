package com.mubasher.oms.dfixrouter.server.oms;

import com.isi.security.GNUCrypt;
import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.logs.LogEventsEnum;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.WatchDogHandler;
import com.objectspace.jgl.Queue;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.security.InvalidKeyException;

public class MiddlewareHandler {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.oms.MiddlewareHandler");
    protected final Queue queue;
    protected volatile boolean isActive = true;
    protected String ip;
    protected String dfixId;
    private String hostId;
    protected volatile boolean isConnected = false;
    protected QueueExceptionListener<MiddlewareHandler> exceptionListener;
    protected volatile boolean isRunning = false;
    private boolean isEnableWatchdog;
    private Host host;

    protected MiddlewareHandler(Host host) {
        this.ip = host.getIp();
        this.host = host;
        this.dfixId = SettingsConstants.CLUSTER_MEMBER_PREFIX + "-" + Settings.getProperty(IConstants.SETTING_DFIX_ID);
        this.hostId = IConstants.HOST_MEMBER_PREFIX + "-" + host.getOmsId();
        queue = new Queue();
        isEnableWatchdog = IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG));
    }

    public void setConnected() {
        sendLinkStatusConnected();
        isConnected = true;
    }

    private void sendLinkStatusConnected() {
        if (isEnableWatchdog) {
            WatchDogHandler.sendLinkStatus(hostId, dfixId, com.dfn.watchdog.commons.State.CONNECTED);
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public QueueExceptionListener<MiddlewareHandler> getQueueExceptionListener() {
        return exceptionListener;
    }

    protected void sendToExchange(Message message, String queueName) {
        try {
            String strMessage = getStringMessage(message);
            if (strMessage != null) {
                StringBuilder sb = new StringBuilder("Message received from App Server: ").append(ip)
                        .append(" through Queue: ").append(queueName)
                        .append(" Message: ").append(strMessage);
                logger.elklog(LogEventsEnum.REQUEST_RECEIVED, sb.toString());
                logger.info(LogEventsEnum.REQUEST_RECEIVED, sb.toString());
                DFIXRouterManager.getToExchangeQueue().processMessage(strMessage);
            }
        } catch (Exception e) {
            logger.error("Exception at sending msg to exchange: " + e.getMessage(), e);
        }
    }

    protected String getStringMessage(Message message) throws JMSException {
        String strMessage = null;
        if (message instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) message;
            if (textMessage.getText() != null) {
                strMessage = textMessage.getText();
            }
        } else if (message instanceof MapMessage) {
            MapMessage mapMessage = (MapMessage) message;
            DFIXMessage dfixMessage = new DFIXMessage();
            if (mapMessage.getString(IConstants.MAP_EXCHANGE) != null) {
                dfixMessage.setExchange(mapMessage.getString(IConstants.MAP_EXCHANGE));
            }
            if (mapMessage.getString(IConstants.MAP_MESSAGE) != null) {
                dfixMessage.setMessage(mapMessage.getString(IConstants.MAP_MESSAGE));
            } else if (mapMessage.getString(IConstants.MAP_EVENT_DATA) != null) {
                dfixMessage.setMessage(mapMessage.getString(IConstants.MAP_EVENT_DATA));
            }
            if (mapMessage.getString(IConstants.MAP_TYPE) != null) {
                dfixMessage.setType(mapMessage.getString(IConstants.MAP_TYPE));
            } else if (mapMessage.getString(IConstants.MAP_EVENT_TYPE) != null) {
                dfixMessage.setType(mapMessage.getString(IConstants.MAP_EVENT_TYPE));
            }
            if (mapMessage.getString(IConstants.MAP_SEQUENCE) != null) {
                dfixMessage.setSequence(mapMessage.getString(IConstants.MAP_SEQUENCE));
            }
            strMessage = dfixMessage.composeMessage();
        }
        return strMessage;
    }

    public boolean isActive() {
        return isActive;
    }

    public Queue getQueue() {
        return queue;
    }

    public void setDisConnected() {
        sendLinkStatusDisconnected();
        isConnected = false;
    }

    private void sendLinkStatusDisconnected() {
        if (isEnableWatchdog) {
            WatchDogHandler.sendLinkStatus(hostId, dfixId, com.dfn.watchdog.commons.State.CLOSED);
        }
    }

    public boolean isEnableWatchdog() {
        return isEnableWatchdog;
    }

    public String getHostId() {
        return hostId;
    }

    public Host getHost() {
        return host;
    }

    public String decryptHostPassword(String password) {
        try {
            if (password == null){
                return null;
            }
            return GNUCrypt.decrypt(IConstants.GNU_DECRYPT_KEY, password);
        } catch (InvalidKeyException e) {
            logger.error("Failed to decrypt the host password : ", e);
            throw new RuntimeException("Failed to decrypt the host password :", e);
        }
    }
}
