package com.mubasher.oms.dfixrouter.server.oms;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.logs.LogEventsEnum;
import com.mubasher.oms.dfixrouter.server.fix.FIXMessageProcessor;
import com.mubasher.oms.dfixrouter.system.Settings;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;

/**
 * Created by IntelliJ IDEA.
 * User: YASANTHA
 * Date: Jun 25, 2004
 * Time: 11:33:47 AM
 * To change this template use File | Settings | File Templates.
 */
public class MQSender extends MQHandler implements MiddlewareSender {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.oms.MQSender");
    private MessageProducer messageProducer;
    private boolean groupingEnabled;
    private boolean isMqCluster;

    public MQSender(Host host, String queueName) {
        super(host);
        this.queueName = queueName;
        this.groupingEnabled = IConstants.SETTING_YES.equals(Settings.getProperty(SettingsConstants.EXCHANGE_LEVEL_GROUPING));
        exceptionListener = new QueueExceptionListener<>(this);
        this.isMqCluster = IConstants.SETTING_YES.equals(Settings.getProperty(SettingsConstants.IS_MQ_CLUSTER));
        logger.debug("Creating MQSender for host: " + host.toString());
    }

    @Override
    public void close() {
        logger.info("Closing QueueConnection and QueueSession :" + toString());
        closeQueueManager();
    }

    @Override
    public void closeQueueManager() {
        try {
            connection.close();
            messageProducer.close();
            session.close();
            setDisConnected();
            logger.info("MQSender for Queue : " + destination + " is Closed.");
        } catch (Exception e) {
            logger.error("Cannot close the MQSender : " + destination);
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void send(DFIXMessage msg) throws JMSException {
        MessageProducer messageProd = this.messageProducer;
        TextMessage sendMsg = session.createTextMessage();
        if (groupingEnabled) {
            sendMsg.setStringProperty(SettingsConstants.JMS_GRP_ID_PROPERTY, msg.getMsgGroupId());
        }
        populateCustomPropertiesForMqCluster(msg, sendMsg);
        String message = msg.composeMessage();
        sendMsg.setText(message);
        messageProd.send(sendMsg);
        final StringBuilder logMsg = new StringBuilder("IP: ").append(ip)
                .append(" Sent through Queue: ").append(messageProducer.getDestination())
                .append(" Message: ").append(message);
        if (isMqCluster) {
            logMsg.append(" appServerId : ").append(sendMsg.getStringProperty("appServerId"))
                    .append(" customerID : ").append(sendMsg.getStringProperty("customerID"))
                    .append(" tenantCode : ").append(sendMsg.getStringProperty("tenantCode"));
        }
        logger.info(logMsg.toString());
        logger.info(LogEventsEnum.SENT_TO_OMS, "Message sent: " + msg.toString());
        logger.elklog(LogEventsEnum.SENT_TO_OMS, "Message sent: " + msg.toString());
    }

    private void populateCustomPropertiesForMqCluster(DFIXMessage msg, TextMessage sendMsg) throws JMSException {
        if(isMqCluster) {// header parameters for MQ Cluster Set up
            // Fixtag10008 is read from fixmessage itself if falcon enabled ( this tag is not set for DI messages and session status updates messages)
            // else DFIXMessage.fixTag10008 will be used
            String cusId = isEnableWatchdog() ? FIXMessageProcessor.getFixTagValueOrDefault(msg.getMessage(),FixConstants.FIX_TAG_10008, IConstants.STRING_MINUS_1) : String.valueOf(msg.getFixTag10008());
            sendMsg.setStringProperty("appServerId", String.valueOf(getHost().getOmsId()));
            sendMsg.setStringProperty("customerID", cusId);
            sendMsg.setStringProperty("tenantCode", FIXMessageProcessor.getTenantCode(msg.getMessage()));
        }
    }

    @Override
    public void initializeConnection() {
        try {
            super.createSession(queueName);
            messageProducer = session.createProducer(destination);
            connection.setExceptionListener(exceptionListener);
            setConnected();
            logger.debug("MQSender " + ip + ":" + queueName + " started...");
        } catch (Exception e) {
            getQueueExceptionListener().startConnectionProcess(e);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MQSender{");
        sb.append(super.toString());
        sb.append('}');
        return sb.toString();
    }
}
