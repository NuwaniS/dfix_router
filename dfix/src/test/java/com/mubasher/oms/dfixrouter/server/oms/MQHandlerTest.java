package com.mubasher.oms.dfixrouter.server.oms;

import com.ibm.mq.jms.MQQueue;
import com.ibm.mq.jms.MQQueueConnectionFactory;
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
import javax.jms.QueueSession;

class MQHandlerTest {

    private MQHandler mqHandler;
    MockedStatic<Settings> settingsMockedStatic;

    private Host initiateHost() {
        final Host host = new Host();
        host.setId(1);
        host.setIp("127.0.0.1");
        host.setPort(1099);
        host.setReqQName("FromAppia");
        host.setResQName("ToAppia");
        host.setClubQName("ClubbedExecutionQueue");
        host.setMiddleware(IConstants.MIDDLEWARE_MQ);
        host.setContextFactory("com.ibm.websphere.naming.WsnInitialContextFactory");
        host.setProviderURL("jnp://127.0.0.1:1099");
        host.setConnectionFactory("ConnectionFactory");
        host.setUserName("Test");
        host.setPassword("ZZTpR8pbf+6LVeybU+APuQ==");
        host.setUrlPkgPrefixes("java.naming.corba.orb");
        host.setUrlPkgPrefixesValue("org.omg.CORBA.ORB.init((String[])null,null)");
        host.setChannel("CLIENT.TO.MBSHQM");
        host.setMqQueueManager("FIXQM");
        return host;
    }

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG)).thenReturn(IConstants.SETTING_YES);
        settingsMockedStatic.when(()->Settings.getProperty(IConstants.SETTING_DFIX_ID)).thenReturn("1");
        mqHandler = Mockito.spy(new MQHandler(initiateHost()));
    }

    @AfterEach
    public void tearDown() {
        settingsMockedStatic.close();
    }

    /**
     * If failed, Please check the following files are loaded as test scope. They are available in \src\test\resources\ folder
     * and added to the class path as test dependencies.
     * 1. dhbcore-1.0.jar
     * 2. jmqi-1.0.jar
     */
    @Test
    void createSession_flowTest() throws Exception {
        MQQueueConnectionFactory cf = Mockito.mock(MQQueueConnectionFactory.class);
        QueueConnection connection = Mockito.mock(QueueConnection.class);
        QueueSession session = Mockito.mock(QueueSession.class);
        MQQueue destination = Mockito.mock(MQQueue.class);
        Mockito.doReturn(cf).when(mqHandler).getQueueConnectionFactory();
        Mockito.doReturn(connection).when(cf).createQueueConnection(Mockito.anyString(),Mockito.anyString());
        Mockito.doReturn(session).when(connection).createQueueSession(Mockito.anyBoolean(), Mockito.anyInt());
        Mockito.doReturn(destination).when(session).createQueue(Mockito.anyString());
        mqHandler.createSession(Mockito.anyString());
    }

    @Test
    void createDestination_coverageTest() throws Exception {
        MQQueueConnectionFactory cf = Mockito.mock(MQQueueConnectionFactory.class);
        QueueConnection connection = Mockito.mock(QueueConnection.class);
        QueueSession session = Mockito.mock(QueueSession.class);
        Queue destination = Mockito.mock(Queue.class);
        Mockito.doReturn(cf).when(mqHandler).getQueueConnectionFactory();
        Mockito.doReturn(connection).when(cf).createQueueConnection(Mockito.anyString(),Mockito.anyString());
        Mockito.doReturn(session).when(connection).createQueueSession(Mockito.anyBoolean(), Mockito.anyInt());
        Mockito.doReturn(destination).when(session).createQueue(Mockito.anyString());
        mqHandler.createSession(Mockito.anyString());
    }

    @Test
    void getQueueConnectionFactory_flowTest() throws Exception {
        Assertions.assertNotNull(mqHandler.getQueueConnectionFactory(), "QueueConnectionFactory has to be returned");
    }

    @Test
    void toString_flowTest() {
        Assertions.assertTrue(mqHandler.toString().contains(mqHandler.ip), "MQHandler should contain IP details.");
        Assertions.assertTrue(mqHandler.toString().contains(String.valueOf(mqHandler.port)), "MQHandler should contain PORT details.");
    }
}
