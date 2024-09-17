package com.mubasher.oms.dfixrouter.server.oms;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.QueueReceiver;

/**
 * this class listen to the OMS queue and read the messages from it
 */
public class JMSListener extends JMSHandler implements MiddlewareListener {
    private final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.oms.JMSListener");
    private QueueReceiver queueReceiver = null;

    public JMSListener(Host host) {
        super(host);
        this.queueName = host.getReqQName() ;
        exceptionListener = new QueueExceptionListener<>(this);
        logger.debug("Creating JMSListener for host: " + host.toString() + " - " + queueName);
    }

    @Override
    public void close() {
        isActive = false;
        closeQueueManager();
    }

    @Override
    public void initializeConnection() {
        logger.debug("initializeConnection started..");

        try {
            queueSession = super.createQueueSession();
            if (queueReceiver != null) {
                queueReceiver.close();
            }
            queueReceiver = queueSession.createReceiver(myQueue);
            queueReceiver.setMessageListener(this);
            queueConnection.start();
            logger.info("Listener Queue Connection Started : " + ip + " - " + queueName);
            setConnected();
        } catch (Exception e) {
            getQueueExceptionListener().startConnectionProcess(e);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("JMSListener{");
        sb.append(super.toString());
        sb.append('}');
        return sb.toString();
    }

    public void closeQueueManager() {
        if (queueConnection != null) {
            try {
                queueConnection.close();
                queueSession.close();
                queueConnection = null;
                logger.info("Terminated Queue Connection and Session : " + ip);
            } catch (JMSException e) {
                logger.error("Cannot close the JMS...." + ip + e);
            } catch (Exception e) {
                logger.error("Problem in closing jsm resources " + ip + e);
            }
        }
        setDisConnected();
    }

    @Override
    public void onMessage(Message message) {
        sendToExchange(message, queueName);
    }
}
