package com.mubasher.oms.dfixrouter.sender;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.server.oms.JMSSender;
import com.mubasher.oms.dfixrouter.server.oms.MQSender;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareSender;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by janakar on 10/7/2016.
 */
public class MessageSender {
    public static final String PRIMARY_QUEUE_KEY = "PRIMARY_QUEUE";
    public static final String INTERMEDIATE_QUEUE_KEY = "INTERMEDIATE_QUEUE";
    public static final String U_MESSAGE_QUEUE_KEY = "U_MESSAGE_QUEUE";
    public static final String CLUBB_QUEUE_NAME = "CLUBB_QUEUE_NAME";


    private MessageSender() {
        super();
    }

    public static Map<String, MiddlewareSender> getQSender(Host host) throws DFIXConfigException {
        HashMap<String, MiddlewareSender> middlewareSender = null;
        if (host != null) {
            middlewareSender = new HashMap<>();
            if (host.getResQName() != null) {
                if (IConstants.MIDDLEWARE_JMS.equalsIgnoreCase(host.getMiddleware())) {
                    middlewareSender.put(PRIMARY_QUEUE_KEY, new JMSSender(host, host.getResQName()));
                } else if (IConstants.MIDDLEWARE_MQ.equalsIgnoreCase(host.getMiddleware())) {
                    middlewareSender.put(PRIMARY_QUEUE_KEY, new MQSender(host, host.getResQName()));
                }
            } else {
                throw new DFIXConfigException("To Queue is not configured for Host: " + host.toString());
            }

            for (int i = 0; host.getIntermediateQueue() != null && i < host.getIntermediateQueueCount(); i++) {
                String key = getIntermediateQueueKey(i);
                String intermediateQueueName = host.getIntermediateQueue() + i;
                middlewareSender.put(key, new JMSSender(host, intermediateQueueName));
            }
            if (host.getIcmDropCopyQueueCount() > IConstants.CONSTANT_ZERO_0) {
                for (int i = host.getIntermediateQueueCount(); i < (host.getIntermediateQueueCount() + host.getIcmDropCopyQueueCount()); i++) {
                    String key = getIntermediateQueueKey(i);
                    String intermediateQueueName = host.getIntermediateQueue() + i;
                    middlewareSender.put(key, new JMSSender(host, intermediateQueueName));
                }
            }
            if (host.getuMessageQueue() != null) {
                middlewareSender.put(U_MESSAGE_QUEUE_KEY, new JMSSender(host, host.getuMessageQueue()));
            }
            if (host.getClubQName() != null) {
                middlewareSender.put(CLUBB_QUEUE_NAME, new JMSSender(host, host.getClubQName()));
            }
        }
        return middlewareSender;
    }

    public static String getIntermediateQueueKey(int i) {
        return INTERMEDIATE_QUEUE_KEY + "_" + i;
    }

    public static void startSender(Collection<MiddlewareSender> values) {
        for (MiddlewareSender middlewareSender :
                values) {
            middlewareSender.initializeConnection();
        }
    }
}
