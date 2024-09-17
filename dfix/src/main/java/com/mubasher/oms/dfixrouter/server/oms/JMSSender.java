package com.mubasher.oms.dfixrouter.server.oms;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.logs.LogEventsEnum;
import com.mubasher.oms.dfixrouter.system.Settings;

import javax.jms.JMSException;
import javax.jms.MessageProducer;


public class JMSSender extends JMSHandler implements MiddlewareSender {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.oms.JMSSender");
    private MessageProducer messageProducer = null;
    private boolean groupingEnabled;

    public JMSSender(Host host, String queueName) {
        super(host);
        this.queueName = queueName;
        this.groupingEnabled = IConstants.SETTING_YES.equals(Settings.getProperty(SettingsConstants.EXCHANGE_LEVEL_GROUPING));
        exceptionListener = new QueueExceptionListener<>(this);
        logger.debug("Creating JMSSender for host: " + host.toString());
    }

    public void close() {
        isActive = false;
        closeQueueManager();
    }

    public void closeQueueManager() {
        logger.info("Closing QueueConnection and QueueSession :" + toString());
        if (queueConnection != null) {
            try {
                if (isConnected && queue.size() > 0) {
                    logger.info("Waiting until the queue +" + toString() + " emptied.");
                    while (!getQueue().isEmpty()) {
                        DFIXMessage dfixMessage = (DFIXMessage) getQueue().front();
                        send(dfixMessage);
                        getQueue().pop();
                    }
                }
                queueSession.close();
                queueConnection.close();
                queueConnection = null;
                logger.info("Closed QueueConnection and QueueSession :" + providerURL);
            } catch (Exception e) {
                logger.error("Problem in closing jsm resources" + providerURL);
                logger.error(e.getMessage(), e);
            }
        }
        setDisConnected();
    }

    public synchronized void send(DFIXMessage msg) throws JMSException {
        if (groupingEnabled) {
            textMessage.setStringProperty(SettingsConstants.JMS_GRP_ID_PROPERTY, msg.getMsgGroupId());
        }
        String message = msg.composeMessage();
        textMessage.setText(message);
        messageProducer.send(textMessage);
        logger.info((new StringBuilder("IP: ").append(providerURL)
                .append(" Sent through Queue: ").append(queueName)
                .append(" Message: ").append(message)).toString());
        logger.info(LogEventsEnum.SENT_TO_OMS, "Message sent: " + msg.toString());
        logger.elklog(LogEventsEnum.SENT_TO_OMS, "Message sent: " + msg.toString());
    }

    @Override
    public void initializeConnection() {
        try {
            queueSession = super.createQueueSession();
            messageProducer = queueSession.createProducer(myQueue);
            setConnected();
            logger.info("QueueConnection Started " + providerURL + ":" + queueName);
        } catch (Exception e) {
            getQueueExceptionListener().startConnectionProcess(e);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("JMSSender{");
        sb.append(super.toString());
        sb.append('}');
        return sb.toString();
    }
}
