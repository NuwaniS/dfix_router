package com.mubasher.oms.dfixrouter.server.exchange;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.sender.MessageSender;
import com.mubasher.oms.dfixrouter.server.oms.JMSSender;
import com.mubasher.oms.dfixrouter.server.oms.MQSender;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareSender;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import com.mubasher.oms.dfixrouter.util.WatchDogHandler;
import com.objectspace.jgl.Queue;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import quickfix.Message;
import quickfix.field.MsgType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;

/**
 * Created by isharaw on 9/28/2017.
 */
class FromExchangeQueueTest {

    FromExchangeQueue fromExchangeQueueTest;
    private MockedStatic<Settings> settingsMockedStatic;
    private MiddlewareSender middlewareMQSender;
    private MiddlewareSender middlewareJMSSender;
    private Queue queue;
    List<Map<String, MiddlewareSender>> senders;
    @BeforeEach
    public void setup() {
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        settingsMockedStatic.when(()->Settings.getInt(SettingsConstants.HOSTS_COUNT)).thenReturn(IConstants.CONSTANT_ONE_1);
        fromExchangeQueueTest = Mockito.spy(new FromExchangeQueue());

        middlewareMQSender = Mockito.mock(MQSender.class);
        middlewareJMSSender = Mockito.mock(JMSSender.class);
        HashMap<String, MiddlewareSender> middlewareSenderHashMap = new HashMap<>();
        middlewareSenderHashMap.put("MQSender", middlewareMQSender);
        middlewareSenderHashMap.put(MessageSender.PRIMARY_QUEUE_KEY, middlewareJMSSender);
        senders = new ArrayList<>();
        senders.add(middlewareSenderHashMap);
        fromExchangeQueueTest.setSenders(senders);
        queue = new Queue();
        Mockito.when(middlewareJMSSender.getQueue()).thenReturn(queue);
    }

    @AfterEach
    public void tearDown(){
        settingsMockedStatic.close();
    }

    @Test
    void startSenderQueueConnection_ForJMSTest() throws Exception {
        Host host = new Host();
        host.setResQName("queue/FromAppia");
        host.setMiddleware(SettingsConstants.MIDDLEWARE_JMS);
        ArrayList<Host> hostList = new ArrayList<>();
        hostList.add(host);
        settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
        fromExchangeQueueTest.startSenderQueueConnection();
        settingsMockedStatic.verify(Settings::getHostList, Mockito.times(IConstants.CONSTANT_ONE_1));
    }

    @Test
    void startSenderQueueConnection_ForMQTest() throws Exception {
        Host host = new Host();
        host.setResQName("FromAppia");
        host.setMiddleware(SettingsConstants.MIDDLEWARE_MQ);
        ArrayList<Host> hostList = new ArrayList<>();
        hostList.add(host);
        settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
        try (MockedStatic<MessageSender> messageSenderMockedStatic = Mockito.mockStatic(MessageSender.class)){
//            Mockito.doNothing().when(MessageSender), "startSender", Mockito.any(MQSender.class));
            fromExchangeQueueTest.startSenderQueueConnection();
            settingsMockedStatic.verify(Settings::getHostList, Mockito.times(IConstants.CONSTANT_ONE_1));
        }
    }


    @Test
    void startSenderQueueConnection_ForNullTest() throws Exception {
        settingsMockedStatic.when(Settings::getHostList).thenReturn(null);
        Assertions.assertThrows(Exception.class, () -> fromExchangeQueueTest.startSenderQueueConnection());
    }

    @Test
    void stopSenderQueueConnectionTest() {
        Mockito.doNothing().when(middlewareMQSender).close();
        Mockito.doNothing().when(middlewareJMSSender).close();
        fromExchangeQueueTest.stopSenderQueueConnection();
        Mockito.verify(middlewareMQSender, Mockito.times(1)).close();
        Mockito.verify(middlewareJMSSender, Mockito.times(1)).close();
    }

    @Test
    void sendMessageToAll_Test() throws NoSuchFieldException, IllegalAccessException {
        List<Host> hostList = new ArrayList<>();
        hostList.add(new Host());
        settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
        DFIXMessage dfixMessage = new DFIXMessage();

        //middlewareSender disconnected, and watchdog disabled sender count =1 => store message to the queue to be sent later
        fromExchangeQueueTest.sendMessageToAll(dfixMessage);
        Assertions.assertEquals(dfixMessage, queue.pop());

    }

    @Test
    void sendMessageToAll1_Test() throws NoSuchFieldException, IllegalAccessException {
        List<Host> hostList = new ArrayList<>();
        hostList.add(new Host());
        settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
        DFIXMessage dfixMessage = new DFIXMessage();

        TestUtils.setPrivateField(fromExchangeQueueTest,"senderCount", IConstants.CONSTANT_TWO_2);
        final List<Map<String, Integer>> intermediateQueueData = (List<Map<String, Integer>>) TestUtils.getPrivateField(fromExchangeQueueTest, "intermediateQueueData");
        intermediateQueueData.add(Mockito.mock(HashMap.class));
        intermediateQueueData.add(Mockito.mock(HashMap.class));
        final Host host2 = new Host();
        host2.setId(IConstants.CONSTANT_TWO_2);
        host2.setOmsId(IConstants.CONSTANT_TWO_2);
        hostList.add(host2);
        dfixMessage.setServedBy(IConstants.CONSTANT_TWO_2);
        senders.add(senders.get(IConstants.CONSTANT_ZERO_0));
        fromExchangeQueueTest.sendMessageToAll(dfixMessage);
        Assertions.assertEquals(IConstants.CONSTANT_TWO_2,queue.size());
    }

    @Test
    void sendMessageToAll12_Test() throws NoSuchFieldException, IllegalAccessException {
        List<Host> hostList = new ArrayList<>();
        hostList.add(new Host());
        settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
        DFIXMessage dfixMessage = new DFIXMessage();

        //middlewareSender connected, message added to the queue and will be processed
        Mockito.when(middlewareJMSSender.isConnected()).thenReturn(IConstants.CONSTANT_TRUE);
        Mockito.doCallRealMethod().when(middlewareJMSSender).run();
        final ExecutorService executorService = Executors.newFixedThreadPool(IConstants.CONSTANT_ONE_1);
        TestUtils.setPrivateField(fromExchangeQueueTest,"executorService", executorService);
        fromExchangeQueueTest.sendMessageToAll(dfixMessage);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(()->queue.isEmpty());
        Assertions.assertTrue(queue.isEmpty());
        executorService.shutdownNow();
    }

    @ParameterizedTest
    @ValueSource(strings = {MsgType.TRADING_SESSION_STATUS, MsgType.TRADE_CAPTURE_REPORT, MsgType.TRADE_CAPTURE_REPORT_ACK, FixConstants.FIX_VALUE_35_VIEW_ACCOUNT_RESPONSE})
    void determineQueueKey_Test(String msgType) throws Exception {
        DFIXMessage dfixMessage = new DFIXMessage();
        Message message = new Message();
        message.setString(FixConstants.FIX_TAG_100000_DUMMY_COUNT, "100");
        dfixMessage.setMessage(message.toString());
        int serverId =  IConstants.CONSTANT_ZERO_0;
        dfixMessage.setFixMsgType(msgType);
        final String queueKey = (String) TestUtils.invokePrivateMethod(fromExchangeQueueTest, "determineQueueKey", new Class[]{DFIXMessage.class, int.class}, dfixMessage, serverId);
        Assertions.assertEquals(MessageSender.PRIMARY_QUEUE_KEY, queueKey);
        if (msgType.equals(FixConstants.FIX_VALUE_35_VIEW_ACCOUNT_RESPONSE)) {
            Assertions.assertFalse(dfixMessage.getMessage().contains(FixConstants.FIX_TAG_100000_DUMMY_COUNT+"="));
            Assertions.assertTrue(dfixMessage.getMessage().contains(FixConstants.FIX_TAG_9710_COUNT+"=100"));
        }
    }

    @Test
    void storeMessageToSendLater_Test() throws NoSuchFieldException, IllegalAccessException {
        DFIXMessage dfixMessage = new DFIXMessage();
        dfixMessage.setServedBy(IConstants.CONSTANT_ONE_1);
        int serverId =  IConstants.CONSTANT_ZERO_0;
        fromExchangeQueueTest.storeMessageToSendLater(dfixMessage, serverId);
        final Map<Integer, List<DFIXMessage>> resendMsgList = (Map<Integer, List<DFIXMessage>>) TestUtils.getPrivateField(fromExchangeQueueTest, "resendMsgList");
        Assertions.assertTrue(resendMsgList.containsKey(dfixMessage.getServedBy()));
        Assertions.assertTrue(resendMsgList.get(dfixMessage.getServedBy()).contains(dfixMessage));
    }

    @Test
    void resendStoredMessages_Test() throws NoSuchFieldException, IllegalAccessException {
        try (MockedStatic<WatchDogHandler> watchDogHandlerMockedStatic = Mockito.mockStatic(WatchDogHandler.class)) {
            watchDogHandlerMockedStatic.when(()->WatchDogHandler.getAppServerId(any(String.class))).thenReturn(IConstants.CONSTANT_ONE_1);
            int serverId = IConstants.CONSTANT_ONE_1;
            DFIXMessage dfixMessage = new DFIXMessage();
            dfixMessage.setServedBy(serverId);
            Mockito.doNothing().when(fromExchangeQueueTest).sendMessageToSingleServer(dfixMessage,IConstants.CONSTANT_ZERO_0);
            fromExchangeQueueTest.storeMessageToSendLater(dfixMessage, serverId);
            fromExchangeQueueTest.resendStoredMessages(serverId);
            Mockito.verify(fromExchangeQueueTest, Mockito.times(IConstants.CONSTANT_ONE_1)).addMsg(dfixMessage);
            final Map<Integer, List<DFIXMessage>> resendMsgList = (Map<Integer, List<DFIXMessage>>) TestUtils.getPrivateField(fromExchangeQueueTest, "resendMsgList");
            Assertions.assertFalse(resendMsgList.containsKey(dfixMessage.getServedBy()));
        }
    }
}
