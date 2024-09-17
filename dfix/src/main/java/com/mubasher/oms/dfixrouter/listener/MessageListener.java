package com.mubasher.oms.dfixrouter.listener;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.oms.JMSListener;
import com.mubasher.oms.dfixrouter.server.oms.MQListener;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareListener;
import com.mubasher.oms.dfixrouter.system.Settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by janakar on 10/7/2016.
 */
public class MessageListener {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.listener.MessageListener");

    private MessageListener() {
        super();
    }

    public static List<MiddlewareListener> getQListener(Host host) throws DFIXConfigException {
        if (host != null) {
            String reqQName = host.getReqQName();
            if (reqQName == null) {
                if (IConstants.SETTING_YES.equals(Settings.getProperty(SettingsConstants.IS_MQ_CLUSTER)) && host.getId() > 1) {
                    logger.error("From Queue is not configured for Host: " + host + "and Ignored since MQ Cluster setup");
                } else {
                    throw new DFIXConfigException("From Queue is not configured for Host: " + host);
                }
            } else {
                return populateMiddlewareListeners(host, reqQName);
            }
        }
        return new ArrayList<>();
    }

    private static List<MiddlewareListener> populateMiddlewareListeners(Host host, String reqQName) throws DFIXConfigException {
        List<MiddlewareListener> middlewareListener = new ArrayList<>();
        int a = 0;
        do {
            if (a > 0) {
                host.setReqQName(reqQName + a);
            }
            if (IConstants.MIDDLEWARE_JMS.equalsIgnoreCase(host.getMiddleware())) {
                middlewareListener.add(new JMSListener(host));
            } else if (IConstants.MIDDLEWARE_MQ.equalsIgnoreCase(host.getMiddleware())) {
                if (IConstants.SETTING_YES.equals(Settings.getProperty(SettingsConstants.IS_MQ_CLUSTER)) && host.getId() > IConstants.CONSTANT_ONE_1) {
                    throw new DFIXConfigException("From Queue should not be configured in MQ_CLUSTER setup for Host : " + host + " (Only the host 1 expected have HOST1_FROM_QUEUE_COUNT ");
                } else {
                    middlewareListener.add(new MQListener(host));
                }
            }
            ++a;
        } while (a < host.getReqQCount());
        return middlewareListener;
    }

    public static void startListener(Collection<MiddlewareListener> values) {
        for (MiddlewareListener middlewareListener :
                values) {
            logger.info("Start Listener Queue Connection: " + middlewareListener.toString());
            middlewareListener.initializeConnection();
        }
    }

    public static void stopListener(Collection<MiddlewareListener> values) {
        for (MiddlewareListener middlewareListener :
                values) {
            logger.info("Stop Listener Queue Connection: " + middlewareListener.toString());
            middlewareListener.close();
        }
    }
}
