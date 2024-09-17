package com.mubasher.oms.dfixrouter.server.oms;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.objectspace.jgl.Queue;

import javax.jms.JMSException;

/**
 * Created by IntelliJ IDEA.
 * User: Niroshan Serasinghe
 * Date: Apr 18, 2007
 * Time: 5:29:29 PM
 * To change this template use File | Settings | File Templates.
 */
public interface MiddlewareSender extends Runnable {
    void close();

    void closeQueueManager();

    boolean isRunning();

    void setRunning(boolean b);

    default void run() {
        try {
            if (!isConnected()) {
                initializeConnection();
            } else {
                while (!getQueue().isEmpty()) {
                    DFIXMessage dfixMessage = (DFIXMessage) getQueue().front();
                    send(dfixMessage);
                    getQueue().pop();
                }
                setRunning(false);
            }
        } catch (JMSException e) {
            setRunning(false);
            getQueueExceptionListener().onException(e);
        }
    }

    boolean isConnected();

    void initializeConnection();

    Queue getQueue();

    void send(DFIXMessage stringData) throws JMSException;

    QueueExceptionListener<MiddlewareHandler> getQueueExceptionListener();
}
