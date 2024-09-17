package com.mubasher.oms.dfixrouter.server.oms;

import javax.jms.MessageListener;

/**
 * Created by IntelliJ IDEA.
 * User: Niroshan Serasinghe
 * Date: Apr 18, 2007
 * Time: 5:17:49 PM
 * To change this template use File | Settings | File Templates.
 */
public interface MiddlewareListener extends MessageListener {

    void close();

    boolean isConnected();

    void initializeConnection();

    boolean isActive();

}
