package com.mubasher.oms.dfixrouter.server.oms;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;

public class QueueExceptionListener<G extends MiddlewareHandler> extends Thread implements ExceptionListener {
    private final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.oms.QueueExceptionListener");
    private final G middlewareHandler;

    public QueueExceptionListener(G middlewareHandler) {
        this.middlewareHandler = middlewareHandler;
    }

    @Override
    public void onException(JMSException e) {
        startConnectionProcess(e);
        reRouteOMSMessages();   //Reroute the messages to be sent to OMS when watchdog is enabled
    }

    public synchronized void startConnectionProcess(Exception e) {
        logger.error("Exception " + e.getMessage() + " for " + middlewareHandler.toString(), e);
        middlewareHandler.setDisConnected();
        if (middlewareHandler.getQueueExceptionListener().getState() == State.WAITING) {
            logger.info("Reconnecting Process Resumed : " + middlewareHandler.toString());
            middlewareHandler.getQueueExceptionListener().notify();
        } else if (middlewareHandler.getQueueExceptionListener().getState() == State.NEW) {
            logger.info("Reconnecting Process Started : " + middlewareHandler.toString());
            middlewareHandler.getQueueExceptionListener().start();
        } else {
            logger.info("Reconnecting Process status: " + middlewareHandler.getQueueExceptionListener().getState().name());
        }
    }

    @Override
    public void run() {
        while (middlewareHandler.isActive()) {
            if (!middlewareHandler.isConnected()) {
                if (middlewareHandler instanceof MiddlewareSender) {
                    ((MiddlewareSender) middlewareHandler).initializeConnection();
                } else if (middlewareHandler instanceof MiddlewareListener) {
                    ((MiddlewareListener) middlewareHandler).initializeConnection();
                }
                DFIXRouterManager.sleepThread(1000);
            } else {
                logger.info("Reconnected with " + middlewareHandler.toString());
                if (middlewareHandler instanceof MiddlewareSender) {
                    logger.info("Pending Messages : " + middlewareHandler.getQueue().size());
                    ((MiddlewareSender) middlewareHandler).run();
                }
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        logger.error("QueueExceptionListener Interrupted " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    public void reRouteOMSMessages() {
        if (middlewareHandler.isEnableWatchdog() && Settings.getInt(SettingsConstants.HOSTS_COUNT) > 1 &&
                middlewareHandler instanceof MiddlewareSender) {
            while (!middlewareHandler.getQueue().isEmpty()) {
                DFIXMessage dfixMessage = (DFIXMessage) middlewareHandler.getQueue().pop();
                DFIXRouterManager.getFromExchangeQueue().storeMessageToSendLater(dfixMessage, dfixMessage.getServedBy());
            }
        }
    }
}
