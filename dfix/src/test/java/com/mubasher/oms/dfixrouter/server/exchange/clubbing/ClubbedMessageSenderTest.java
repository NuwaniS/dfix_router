package com.mubasher.oms.dfixrouter.server.exchange.clubbing;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.sender.MessageSender;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.oms.JMSSender;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareSender;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class ClubbedMessageSenderTest {

    @Spy
    ClubbedMessageSender clubbedMessageSender ;
    @Spy
    DFIXRouterManager dfixRouterManagerTest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void startSenderQueueConnection_FlowTest() {
        Host host = new Host();
        host.setId(1);
        host.setClubQName("queue/ClubbedExecutionQueue");
        host.setMiddleware(IConstants.MIDDLEWARE_JMS);
        ArrayList<Host> hostList = new ArrayList<>();
        hostList.add(host);
        DFIXMessage dfixMessage = new DFIXMessage();
        List<DFIXMessage> dfixMessageList = new ArrayList<>();
        dfixMessageList.add(dfixMessage);
        MiddlewareSender middlewareSender = Mockito.mock(JMSSender.class);
        HashMap<String, MiddlewareSender> middlewareSenderHashMap = new HashMap<>();
        middlewareSenderHashMap.put(MessageSender.CLUBB_QUEUE_NAME, middlewareSender);
        try (
                MockedStatic<Settings> settingsMockedStatic = Mockito.mockStatic(Settings.class);
                MockedStatic<MessageSender> messageSenderMockedStatic = Mockito.mockStatic(MessageSender.class);
                MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);
        ) {
            settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
            messageSenderMockedStatic.when(() -> MessageSender.getQSender(host)).thenReturn(middlewareSenderHashMap);
            dfixRouterManagerMockedStatic.when(DFIXRouterManager::getInstance).thenReturn(dfixRouterManagerTest);
            clubbedMessageSender.addMessages(dfixMessageList);
            Mockito.doNothing().when(clubbedMessageSender).start();
            clubbedMessageSender.start();
        }
    }
}
