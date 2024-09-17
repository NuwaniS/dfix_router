package com.mubasher.oms.dfixrouter.sender;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.server.oms.JMSSender;
import com.mubasher.oms.dfixrouter.server.oms.MQSender;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareSender;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class MessageSenderTest {
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
    void getQSender_jmsTest() throws Exception {
        final int intermediateQueueCount = 2;
        Host host = new Host();
        host.setResQName("queue/FromAppia");
        host.setIntermediateQueue("queue/FromAppiaIntermediate");
        host.setuMessageQueue("queue/FromAppiaIntermediate99");
        host.setClubQName("queue/FromAppia");
        host.setIntermediateQueueCount(intermediateQueueCount);
        host.setMiddleware(IConstants.MIDDLEWARE_JMS);
        MiddlewareSender middlewareSender = MessageSender.getQSender(host).get(MessageSender.PRIMARY_QUEUE_KEY);
        Assertions.assertTrue(middlewareSender instanceof JMSSender, "JMSSender Middleware expected");
        try (MockedStatic<MessageSender> messageSenderMockedStatic = Mockito.mockStatic(MessageSender.class)){
            MessageSender.getIntermediateQueueKey(Mockito.anyInt());
            messageSenderMockedStatic.verify(()->MessageSender.getIntermediateQueueKey(Mockito.anyInt()),Mockito.times(1));
        }
    }

    @Test
    void getQSender_mqTest() throws Exception {
        Host host = new Host();
        host.setResQName("FromAppia");
        host.setMiddleware(IConstants.MIDDLEWARE_MQ);
        MiddlewareSender middlewareSender = MessageSender.getQSender(host).get(MessageSender.PRIMARY_QUEUE_KEY);
        Assertions.assertTrue(middlewareSender instanceof MQSender, "MQSender Middleware expected");
    }

    @Test
    void getQSender_nullTest() {
        Host host = new Host();
        Assertions.assertThrows(Exception.class, () -> MessageSender.getQSender(host).get(MessageSender.PRIMARY_QUEUE_KEY));
    }

    @Test
    void startSender_jmsTest() throws Exception {
        Host host = new Host();
        host.setResQName("queue/FromAppia");
        host.setMiddleware(IConstants.MIDDLEWARE_JMS);
        MiddlewareSender middlewareSender =
                Mockito.spy(MessageSender.getQSender(host).get(MessageSender.PRIMARY_QUEUE_KEY));
        List<MiddlewareSender> middlewareSenders = new ArrayList<>();
        middlewareSenders.add(middlewareSender);
        Mockito.doNothing().when(middlewareSender).initializeConnection();
        MessageSender.startSender(middlewareSenders);
        Mockito.verify(middlewareSender, Mockito.times(1)).initializeConnection();
    }

    @Test
    void getQSender_nullCoverageTest() throws Exception {
        Map<String, MiddlewareSender> middlewaresenders = MessageSender.getQSender(null);
        Assertions.assertNull(middlewaresenders, "Null result expected.");
    }

    @Test
    void getQSender_coverageTest() throws Exception {
        Host host = new Host();
        host.setResQName("ToAppia");
        Map<String, MiddlewareSender> middlewaresenders = MessageSender.getQSender(host);
        Assertions.assertTrue(middlewaresenders.isEmpty(), "Empty list expected");
    }
}
