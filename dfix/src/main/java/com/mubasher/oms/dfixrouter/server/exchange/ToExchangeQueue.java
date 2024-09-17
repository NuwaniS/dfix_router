package com.mubasher.oms.dfixrouter.server.exchange;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.listener.MessageListener;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareListener;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.objectspace.jgl.Queue;

import java.util.List;

/**
 * this keep the messaged before sending to exchange
 */
public class ToExchangeQueue implements Runnable {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.ToExchangeQueue");
    private Queue queue = new Queue();
    protected List<MiddlewareListener>[] listnersArray;

    public ToExchangeQueue() {
        super();
    }

    public void addMsg(String msg) {
        queue.push(msg);
    }

    @Override
    public void run() {
        //send only if queue has messages and FIX client is logged in to the FIX Gateway
        if (!queue.isEmpty()) {
            try {
                String msg = (String) queue.front();
                processMessage(msg);
                queue.pop();// get poped only if no exceptions
            } catch (Exception e) {
                logger.error("Problem in sending message to exchange: " + e.getMessage(), e);
            }
        }
    }

    public void processMessage(String msg) {
        try {
            String[] tags = msg.split(IConstants.FS);//0-Exchange, 1-sequence, 2-type, 3-data
            if (tags.length >= 4) {
                FIXClient.getFIXClient().sendString(tags[3], tags[0]);
            }
        } catch (Exception e) {
            logger.error("Error at message processing for exchange send: " + e.getMessage(), e);
        }
    }

    public List<MiddlewareListener>[] getListener() {
        return listnersArray;
    }

    public void setListener(List<MiddlewareListener>[] listener) {
        this.listnersArray = listener;
    }

    public void startListenerQueueConnection() throws DFIXConfigException {
        listnersArray = new List[Settings.getHostList().size()];
        int i;
        for (Host host : Settings.getHostList()) {
            i = host.getId() - 1;
            listnersArray[i] = MessageListener.getQListener(host);
            MessageListener.startListener(listnersArray[i]);
            logger.debug("Starting Middleware Listener: " + host.getIp());
        }
    }

    public void stopListenerQueueConnection() {
        for (List<MiddlewareListener> listeners : listnersArray) {
            MessageListener.stopListener(listeners);
        }
    }

    public void startListenerQueueConnection(Host host) throws DFIXConfigException {
        List<MiddlewareListener>[] tempList = listnersArray;
        List<MiddlewareListener>[] newList = new List[Settings.getHostList().size()];
        if (tempList == null) {     //This is the first connection
            startListenerQueueConnection();
            return;
        }
        System.arraycopy(tempList, 0, newList, 0, tempList.length);
        newList[host.getId() - 1] = MessageListener.getQListener(host);
        MessageListener.startListener(newList[host.getId() - 1]);
        listnersArray = newList;
        logger.debug("Starting Middleware Listener: " + host.getIp());
    }

    public void stopListenerQueueConnection(int hostId) {
        List<MiddlewareListener> listeners = listnersArray[hostId - 1];
        if (listeners != null) {
            MessageListener.stopListener(listeners);
        }
        listnersArray[hostId - 1] = null;
    }
}
