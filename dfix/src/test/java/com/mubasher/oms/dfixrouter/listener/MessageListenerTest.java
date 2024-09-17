package com.mubasher.oms.dfixrouter.listener;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.server.oms.JMSListener;
import com.mubasher.oms.dfixrouter.server.oms.MQListener;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareListener;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

class MessageListenerTest {

    private MockedStatic<Settings> settingsMockedStatic;

    @BeforeEach
    public void setup(){
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        settingsMockedStatic.when(()->Settings.getInt(SettingsConstants.SETTING_DFIX_ID)).thenReturn(1);
    }
    @AfterEach
    public void tearDown() {
        settingsMockedStatic.close();
    }

    @Test
    void getQListener_jmsTest() throws Exception {
        Host host = new Host();
        host.setMiddleware(IConstants.MIDDLEWARE_JMS);
        host.setReqQCount(2);
        host.setReqQName("queue/FromAppia");
        MiddlewareListener middlewareListener = MessageListener.getQListener(host).get(0);
        Assertions.assertTrue(middlewareListener instanceof JMSListener, "JMSListener Middleware expected");
    }

    @Test
    void getQListener_mqTest() throws Exception {
        Host host = new Host();
        host.setMiddleware(IConstants.MIDDLEWARE_MQ);
        host.setReqQName("ToAppia");
        MiddlewareListener middlewareListener = MessageListener.getQListener(host).get(0);
        Assertions.assertTrue(middlewareListener instanceof MQListener, "MQListener Middleware expected");
    }

    @Test
    void getQListener_coverageTest() throws Exception {
        Host host = new Host();
        host.setReqQName("ToAppia");
        List<MiddlewareListener> middlewareListeners = MessageListener.getQListener(host);
        Assertions.assertTrue(middlewareListeners.isEmpty(), "Empty list expected");
    }

    @Test
    void getQListener_nullTest() {
        //reqQName not configured for JMS/MQ(not MQ_CLUSTER)
        Host host = new Host();
        Assertions.assertThrows(DFIXConfigException.class,()-> MessageListener.getQListener(host));
    }

    @Test
    void getQListener_MQ_CLUSTER_null_Test() throws DFIXConfigException {
        //reqQName not configured for MQ_CLUSTER
        Host host = new Host();
        host.setId(2);
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.IS_MQ_CLUSTER)).thenReturn("Y");
        Assertions.assertTrue(MessageListener.getQListener(host).isEmpty());
    }

    //in MQ cluster setup only the first host will accept FROM_QUEUE,FROM_QUEUE_COUNT configuration . This test validate ,
    // if these values configured for others hosts and MessageListener.getQListener throws DFIXConfigException
    @Test
    void getQListener_MQ_CLUSTER_FromQueue_Test() {
        Host host = new Host();
        host.setId(2);
        host.setReqQName("ToExchange");
        host.setMiddleware(IConstants.MIDDLEWARE_MQ);
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.IS_MQ_CLUSTER)).thenReturn("Y");
        Assertions.assertThrows(DFIXConfigException.class,()-> MessageListener.getQListener(host));
    }

    @Test
    void getQListener_nullCoverageTest() throws Exception {
        List<MiddlewareListener> middlewareListeners = MessageListener.getQListener(null);
        Assertions.assertTrue(middlewareListeners.isEmpty(), "Empty list expected");
    }

    @Test
    void startListener_coverageTest() throws Exception {
        Host host = new Host();
        host.setMiddleware(IConstants.MIDDLEWARE_JMS);
        host.setReqQName("queue/ToAppia");
        MiddlewareListener middlewareListener = Mockito.spy(MessageListener.getQListener(host).get(0));
        Mockito.doNothing().when(middlewareListener).initializeConnection();
        List<MiddlewareListener> middlewareListenerList = new ArrayList<>();
        middlewareListenerList.add(middlewareListener);
        MessageListener.startListener(middlewareListenerList);
        Mockito.verify(middlewareListener, Mockito.times(1)).initializeConnection();
    }

    @Test
    void stopListener_coverageTest() throws Exception {
        Host host = new Host();
        host.setMiddleware(IConstants.MIDDLEWARE_JMS);
        host.setReqQName("queue/ToAppia");
        MiddlewareListener middlewareListener = Mockito.spy(MessageListener.getQListener(host).get(0));
        Mockito.doNothing().when(middlewareListener).close();
        List<MiddlewareListener> middlewareListenerList = new ArrayList<>();
        middlewareListenerList.add(middlewareListener);
        MessageListener.stopListener(middlewareListenerList);
        Mockito.verify(middlewareListener, Mockito.times(1)).close();
    }
}
