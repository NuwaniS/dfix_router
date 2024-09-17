package com.mubasher.oms.dfixrouter.server.exchange;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.listener.MessageListener;
import com.mubasher.oms.dfixrouter.server.oms.JMSListener;
import com.mubasher.oms.dfixrouter.server.oms.MQListener;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareListener;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by isharaw on 9/28/2017.
 */
class ToExchangeQueueTest {

    @Spy
    ToExchangeQueue toExchangeQueueTest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

    }
    @Test
    void startSenderQueueConnection_ForJMSTest() throws Exception{

        Host host = new Host();
        host.setId(1);
        host.setIp("127.0.0.1");
        host.setPort(1099);
        host.setReqQName("queue/FromAppia");
        host.setResQName("queue/ToAppia");
        host.setClubQName("queue/ClubbedExecutionQueue");
        host.setMiddleware("JMS");
        host.setContextFactory("org.jnp.interfaces.NamingContextFactory");
        host.setProviderURL("jnp://127.0.0.1:1099");
        host.setConnectionFactory("ConnectionFactory");
        host.setUserName("Test");
        host.setPassword("Test_password");
        ArrayList<Host> hostList =new ArrayList<>();
        hostList.add(host);
        MiddlewareListener middlewareListener = Mockito.mock(JMSListener.class);
        List<MiddlewareListener> middlewareListenerList = new ArrayList<>();
        middlewareListenerList.add(middlewareListener);
        try (MockedStatic<Settings> settingsMockedStatic  = Mockito.mockStatic(Settings.class);
             MockedStatic<MessageListener> messageListenerMockedStatic = Mockito.mockStatic(MessageListener.class);
        ) {
            settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
            settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.HOSTS_COUNT)).thenReturn("1");
            messageListenerMockedStatic.when(() -> MessageListener.getQListener(host)).thenReturn(middlewareListenerList);
            toExchangeQueueTest.startListenerQueueConnection();
            messageListenerMockedStatic.verify(() -> MessageListener.startListener(middlewareListenerList), Mockito.times(IConstants.CONSTANT_ONE_1));
        }
    }

    @Test
    void startSenderQueueConnection_ForMQTest() throws Exception{

        Host host = new Host();
        host.setId(1);
        host.setIp("127.0.0.1");
        host.setPort(1099);
        host.setReqQName("queue/FromAppia");
        host.setResQName("queue/ToAppia");
        host.setClubQName("queue/ClubbedExecutionQueue");
        host.setMiddleware("MQ");
        host.setContextFactory("org.jnp.interfaces.NamingContextFactory");
        host.setProviderURL("jnp://127.0.0.1:1099");
        host.setConnectionFactory("ConnectionFactory");
        host.setUserName("Test");
        host.setPassword("Test_password");
        ArrayList<Host> hostList =new ArrayList<>();
        hostList.add(host);
        MiddlewareListener middlewareListener = Mockito.mock(MQListener.class);
        List<MiddlewareListener> middlewareListenerList = new ArrayList<>();
        middlewareListenerList.add(middlewareListener);
        try (MockedStatic<Settings> settingsMockedStatic  = Mockito.mockStatic(Settings.class);
             MockedStatic<MessageListener> messageListenerMockedStatic = Mockito.mockStatic(MessageListener.class);
        ) {

            settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
            settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.HOSTS_COUNT)).thenReturn("1");
            messageListenerMockedStatic.when(() -> MessageListener.getQListener(host)).thenReturn(middlewareListenerList);
            toExchangeQueueTest.startListenerQueueConnection();
            messageListenerMockedStatic.verify(() -> MessageListener.startListener(middlewareListenerList), Mockito.times(IConstants.CONSTANT_ONE_1));

        }
    }

    @Test
    void startSenderQueueConnection_ForNullTest() {
        Host host = new Host();
        ArrayList<Host> hostList = new ArrayList<>();
        hostList.add(host);
        try (MockedStatic<Settings> settingsMockedStatic = Mockito.mockStatic(Settings.class)) {
            settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
            settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.HOSTS_COUNT)).thenReturn("1");
            Assertions.assertThrows(Exception.class, () -> toExchangeQueueTest.startListenerQueueConnection());
        }
    }

    @Test
    void stopSenderQueueConnectionTest() {
        List<MiddlewareListener> middlewareListenerList0 = new ArrayList<>();
        List<MiddlewareListener> middlewareListenerList1 = new ArrayList<>();
        MiddlewareListener middlewareMQListner = Mockito.mock(MQListener.class);
        middlewareListenerList0.add(middlewareMQListner);
        MiddlewareListener middlewareJMSListner = Mockito.mock(JMSListener.class);
        middlewareListenerList1.add(middlewareJMSListner);
        List<MiddlewareListener>[] listeners = new ArrayList[2];
        listeners[0] = middlewareListenerList0;
        listeners[1] = middlewareListenerList1;
        toExchangeQueueTest.setListener(listeners);
        Mockito.doNothing().when(listeners[0].get(0)).close();
        Mockito.doNothing().when(listeners[1].get(0)).close();
        toExchangeQueueTest.stopListenerQueueConnection();
        Mockito.verify(listeners[0].get(0), Mockito.times(1)).close();
        Mockito.verify(listeners[1].get(0), Mockito.times(1)).close();
    }

    /*@Test
    public void stopSenderQueueConnection_ExceptionTest() throws Exception{
        List<MiddlewareListener> middlewareListenerList0 = new ArrayList<>();
        MiddlewareListener middlewareMQListner = Mockito.mock(MQListener.class);
        middlewareListenerList0.add(middlewareMQListner);
        List<MiddlewareListener>[] listeners = new ArrayList[1];
        listeners[0] = middlewareListenerList0;
        toExchangeQueueTest.setListener(listeners);
        Mockito.doThrow(MQException.class).when(listeners[0].get(0)).close();
        toExchangeQueueTest.stopListenerQueueConnection();
        Assert.assertTrue(true);
    }*/
}
