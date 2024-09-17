package com.mubasher.oms.dfixrouter.server.oms;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSession;
import javax.naming.InitialContext;

class JMSHandlerTest {

    private JMSHandler jmsHandler;
    private MockedStatic<Settings> settingsMockedStatic ;

    private Host initiateHost() {
        final Host host = new Host();
        host.setId(1);
        host.setIp("127.0.0.1");
        host.setPort(1099);
        host.setReqQName("queue/FromAppia");
        host.setResQName("queue/ToAppia");
        host.setClubQName("queue/ClubbedExecutionQueue");
        host.setMiddleware(IConstants.MIDDLEWARE_JMS);
        host.setContextFactory("org.jnp.interfaces.NamingContextFactory");
        host.setProviderURL("jnp://127.0.0.1:1099");
        host.setConnectionFactory("ConnectionFactory");
        host.setUserName("Test");
        host.setPassword("ZZTpR8pbf+6LVeybU+APuQ==");
        host.setUrlPkgPrefixes("java.naming.factory.url.pkgs");
        host.setUrlPkgPrefixesValue("org.jboss.naming");
        return host;
    }

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG)).thenReturn(IConstants.SETTING_YES);
        settingsMockedStatic.when(()->Settings.getProperty(IConstants.SETTING_DFIX_ID)).thenReturn("1");
        jmsHandler = Mockito.spy(new JMSHandler(initiateHost()));
    }
    @AfterEach
    public void tearDown() {
        settingsMockedStatic.close();
    }


    @Test
    void getContext_flowTest() throws Exception {
        Assertions.assertNotNull(jmsHandler.getContext(), "Initial context has to be returned");
    }

    @Test
    void getContext_mqMiddleware() throws Exception {
        Host host = initiateHost();
        host.setMiddleware(IConstants.MIDDLEWARE_MQ);
        jmsHandler = Mockito.spy(new JMSHandler(host));
        Assertions.assertNotNull(jmsHandler.getContext(), "Initial context has to be returned");
    }

    @Test
    void getContext_withoutUsername() throws Exception {
        Host host = initiateHost();
        host.setUserName(null);
        jmsHandler = Mockito.spy(new JMSHandler(host));
        Assertions.assertNotNull(jmsHandler.getContext(), "Initial context has to be returned");
    }

    @Test
    void getContext_withoutPassword() throws Exception {
        Host host = initiateHost();
        host.setPassword(null);
        jmsHandler = Mockito.spy(new JMSHandler(host));
        Assertions.assertNotNull(jmsHandler.getContext(), "Initial context has to be returned");
    }

    @Test
    void createQueueSession_flowTest() throws Exception {
        InitialContext initialContext = Mockito.mock(InitialContext.class);
        QueueConnectionFactory queueConnectionFactory = Mockito.mock(QueueConnectionFactory.class);
        Queue queue = Mockito.mock(Queue.class);
        QueueConnection queueConnection = Mockito.mock(QueueConnection.class);
        QueueSession queueSession = Mockito.mock(QueueSession.class);
        Mockito.doReturn(initialContext).when(jmsHandler).getContext();
        Mockito.doReturn(queueConnectionFactory).when(initialContext).lookup(jmsHandler.connectionFactory);
        Mockito.doReturn(queue).when(initialContext).lookup(jmsHandler.queueName);
        Mockito.doReturn(queueConnection).when(queueConnectionFactory).createQueueConnection(jmsHandler.userName, jmsHandler.password);
        Mockito.doReturn(queueSession).when(queueConnection).createQueueSession(Mockito.anyBoolean(), Mockito.anyInt());
        Mockito.doNothing().when(initialContext).close();
        Assertions.assertNotNull(jmsHandler.createQueueSession(), "QueueSession context has to be returned");
    }

    @Test
    void createQueueSession_withoutUsername() throws Exception {
        InitialContext initialContext = Mockito.mock(InitialContext.class);
        QueueConnectionFactory queueConnectionFactory = Mockito.mock(QueueConnectionFactory.class);
        Queue queue = Mockito.mock(Queue.class);
        QueueConnection queueConnection = Mockito.mock(QueueConnection.class);
        QueueSession queueSession = Mockito.mock(QueueSession.class);
        Host host = initiateHost();
        host.setUserName(null);
        jmsHandler = Mockito.spy(new JMSHandler(host));
        Mockito.doReturn(initialContext).when(jmsHandler).getContext();
        Mockito.doReturn(queueConnectionFactory).when(initialContext).lookup(jmsHandler.connectionFactory);
        Mockito.doReturn(queue).when(initialContext).lookup(jmsHandler.queueName);
        Mockito.doReturn(queueConnection).when(queueConnectionFactory).createQueueConnection();
        Mockito.doReturn(queueSession).when(queueConnection).createQueueSession(Mockito.anyBoolean(), Mockito.anyInt());
        Mockito.doNothing().when(initialContext).close();
        Assertions.assertNotNull(jmsHandler.createQueueSession(), "QueueSession context has to be returned");
    }

    @Test
    void createQueueSession_withoutPassword() throws Exception {
        InitialContext initialContext = Mockito.mock(InitialContext.class);
        QueueConnectionFactory queueConnectionFactory = Mockito.mock(QueueConnectionFactory.class);
        Queue queue = Mockito.mock(Queue.class);
        QueueConnection queueConnection = Mockito.mock(QueueConnection.class);
        QueueSession queueSession = Mockito.mock(QueueSession.class);
        Host host = initiateHost();
        host.setPassword(null);
        jmsHandler = Mockito.spy(new JMSHandler(host));
        Mockito.doReturn(initialContext).when(jmsHandler).getContext();
        Mockito.doReturn(queueConnectionFactory).when(initialContext).lookup(jmsHandler.connectionFactory);
        Mockito.doReturn(queue).when(initialContext).lookup(jmsHandler.queueName);
        Mockito.doReturn(queueConnection).when(queueConnectionFactory).createQueueConnection();
        Mockito.doReturn(queueSession).when(queueConnection).createQueueSession(Mockito.anyBoolean(), Mockito.anyInt());
        Mockito.doNothing().when(initialContext).close();
        Assertions.assertNotNull(jmsHandler.createQueueSession(), "QueueSession context has to be returned");
    }

    @Test
    void toString_flowTest() {
        Assertions.assertTrue(jmsHandler.toString().contains(jmsHandler.providerURL), "JMSHandler should contain PROVIDER_URL details.");
    }
}
