package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.concurrent;

import com.mubasher.oms.dfixrouter.beans.InternalBean;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by randulal on 11/30/2018.
 */
public class MessageQHandler {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.concurrent.MessageQHandler");
    private static MessageQHandler self = null;
    private static BlockingQueue<InternalBean> internalMessageQueue1 = new LinkedBlockingQueue<>();

    private MessageQHandler() {
        // private constructor to avoid direct instance creation
    }

    public static MessageQHandler getSharedInstance() {
        if (self == null) {
            self = new MessageQHandler();
            self.start();
        }
        return self;
    }

    public BlockingQueue<InternalBean> getMessageQueue(){
        return internalMessageQueue1;
    }

    public void putMessageToQueue(InternalBean internalBean) {
        internalMessageQueue1.add(internalBean);
    }

    public void start() {
        logger.info("starting thread Executor");
        ConcurrentProcessor concurrentProcessor1;
        Thread conProsThread1;
        concurrentProcessor1 = new ConcurrentProcessor(this);
        conProsThread1 = new Thread(concurrentProcessor1, "Executor");
        conProsThread1.start();
    }
}
