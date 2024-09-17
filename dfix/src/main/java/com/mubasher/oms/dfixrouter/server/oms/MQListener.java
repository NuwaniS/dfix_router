package com.mubasher.oms.dfixrouter.server.oms;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;

import javax.jms.Message;
import javax.jms.MessageConsumer;

public class MQListener extends MQHandler implements MiddlewareListener {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.oms.MQListener");
    private MessageConsumer messageConsumer;

    public MQListener(Host host) {
        super(host);
        this.queueName = host.getReqQName();
        exceptionListener = new QueueExceptionListener<>(this);
        logger.debug("Creating MQListener for host: " + host.toString());
    }

    public void close() {
        if (destination != null) {
            try {
                messageConsumer.close();
                session.close();
                connection.close();
                logger.info("MQListener for destination : " + destination.toString() + " is Closed.");
            } catch (Exception e) {
                logger.error("Cannot close the MQListener : " + destination.toString() + " " + e.getMessage(), e);
            }
        }
        setDisConnected();
        isActive = false;
    }

    @Override
    public void initializeConnection() {
        try {
            super.createSession(queueName);
            messageConsumer = session.createConsumer(destination);
            messageConsumer.setMessageListener(this);
            connection.setExceptionListener(exceptionListener);
            // Start the connection
            connection.start();
            setConnected();
            logger.info("MQListener " + ip + ":" + queueName + " started...");
        } catch (Exception e) {
            getQueueExceptionListener().startConnectionProcess(e);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MQListener{");
        sb.append(super.toString());
        sb.append('}');
        return sb.toString();
    }

    @Override
    public void onMessage(Message message) {
        sendToExchange(message, queueName);
    }
}
