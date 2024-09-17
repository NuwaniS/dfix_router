package com.mubasher.oms.dfixrouter.server.exchange.clubbing;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.exchange.FromExchangeQueue;
import com.mubasher.oms.dfixrouter.server.fix.FIXApplication;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.*;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Created by Nilaan L on 7/18/2024.
 */
class ExchangeExecutionMergerTest {

    ExchangeExecutionMerger exchangeExecutionMerger;
    @Spy
    Message msgMock;
    @Mock
    FIXApplication fixApplicationMock;
    @Mock
    FIXClient fixClientMock;
    @Mock
    FromExchangeQueue fromExchangeQueueMock;
    String sessionId = "TDWL-CL";
    public final String cliOrdId = "123456";
    public final String oriCliOrdId = "123454";


    MockedStatic<FIXClient> fixClientStatic;
    MockedStatic<DFIXRouterManager> dfixRouterManagerStatic;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fixClientStatic = Mockito.mockStatic(FIXClient.class);
        dfixRouterManagerStatic = Mockito.mockStatic(DFIXRouterManager.class);
        fixClientStatic.when(FIXClient::getFIXClient).thenReturn(fixClientMock);
        dfixRouterManagerStatic.when(DFIXRouterManager::getFromExchangeQueue).thenReturn(fromExchangeQueueMock);
        Mockito.when(fixClientMock.getApplication()).thenReturn(fixApplicationMock);

        exchangeExecutionMerger = Mockito.spy(new ExchangeExecutionMerger());
        ClubbedMessageSender clubbedMessageSender= Mockito.mock(ClubbedMessageSender.class);
        exchangeExecutionMerger.setClubbedMessageSender(clubbedMessageSender);

        msgMock.setString(ClOrdID.FIELD, cliOrdId);
        msgMock.setInt(LastShares.FIELD,100);
        msgMock.setDouble(LastPx.FIELD,35.5);
        msgMock.setChar(OrdStatus.FIELD,OrdStatus.PARTIALLY_FILLED);
    }

    @AfterEach
    void tearDown() {
        fixClientStatic.close();
        dfixRouterManagerStatic.close();
    }

    @Test
    void addMsg_Test() throws NoSuchFieldException, IllegalAccessException {
        exchangeExecutionMerger.addMsg(msgMock,sessionId);

        final com.objectspace.jgl.Queue queue = (com.objectspace.jgl.Queue) TestUtils.getPrivateField(exchangeExecutionMerger, "queue");
        Assertions.assertEquals(msgMock, queue.pop());
    }

    @Test
    void processExecutionReport_Case1_Test() throws FieldNotFound {
        //New execution report for the clOrdId - no previous messages
        Mockito.doNothing().when(exchangeExecutionMerger).storeExecutionReport(anyString(),any(Message.class));
        exchangeExecutionMerger.getClubbedMsgsMap().clear(); //clubbedMsgsMap does not have cliOrdId
        exchangeExecutionMerger.processExecutionReport(msgMock);

        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ZERO_0)).clubAndStoreExecutionReport(anyString(),any(Message.class),any(Message.class));
        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ZERO_0)).sendClubbedExecutionReport(anyString());
        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).storeExecutionReport(anyString(),any(Message.class));
    }

    @Test
    void processExecutionReport_Case2_Test() throws FieldNotFound {
        //lastPrice of the currentMsg != lastPrice of the previousMsg
        Message clubMessage = Mockito.spy(Message.class);
        clubMessage.setString(ClOrdID.FIELD, cliOrdId);
        clubMessage.setDouble(LastPx.FIELD,35);
        
        Mockito.doNothing().when(exchangeExecutionMerger).storeExecutionReport(anyString(),any(Message.class));
        Mockito.doNothing().when(exchangeExecutionMerger).sendClubbedExecutionReport(anyString());
        exchangeExecutionMerger.getClubbedMsgsMap().put(cliOrdId,clubMessage);
        exchangeExecutionMerger.processExecutionReport(msgMock);
        

        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).sendClubbedExecutionReport(anyString());
        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).storeExecutionReport(anyString(),any(Message.class));
    }

    @Test
    void processExecutionReport_Case3_Test() throws FieldNotFound {
        //lastPrice of the currentMsg != lastPrice of the previousMsg
        Message clubMessage = Mockito.spy(Message.class);
        clubMessage.setString(ClOrdID.FIELD, cliOrdId);
        clubMessage.setDouble(LastPx.FIELD,35.5);

        Mockito.doNothing().when(exchangeExecutionMerger).clubAndStoreExecutionReport(anyString(),any(Message.class),any(Message.class));
        exchangeExecutionMerger.getClubbedMsgsMap().put(cliOrdId,clubMessage);
        exchangeExecutionMerger.processExecutionReport(msgMock);

        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).clubAndStoreExecutionReport(anyString(),any(Message.class),any(Message.class));
    }

    @Test
    void processExecutionReport_Case4_Test() throws FieldNotFound {
        // Execution reports such as queued, filled, replaced, cancelled etc
        msgMock.setString(OrigClOrdID.FIELD, oriCliOrdId);
        msgMock.setChar(OrdStatus.FIELD,OrdStatus.FILLED);

        Mockito.doNothing().when(exchangeExecutionMerger).sendClubbedExecutionReport(anyString());
        Mockito.doNothing().when(exchangeExecutionMerger).sendExecutionReport(any(Message.class));
        exchangeExecutionMerger.getClubbedMsgsMap().clear(); //clubbedMsgsMap does not have cliOrdId
        exchangeExecutionMerger.getClubbedMsgsMap().put(oriCliOrdId,Mockito.mock(Message.class));
        exchangeExecutionMerger.processExecutionReport(msgMock);

        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).sendClubbedExecutionReport(oriCliOrdId);
        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).sendExecutionReport(any(Message.class));
    }

    @Test
    void processExecutionReport_Case5_Test() throws FieldNotFound {
        // Execution reports such as queued, filled, replaced, cancelled etc
        msgMock.setChar(OrdStatus.FIELD,OrdStatus.FILLED);

        Mockito.doNothing().when(exchangeExecutionMerger).sendClubbedExecutionReport(anyString());
        Mockito.doNothing().when(exchangeExecutionMerger).sendExecutionReport(any(Message.class));
        exchangeExecutionMerger.getClubbedMsgsMap().clear(); //clubbedMsgsMap does not have cliOrdId
        exchangeExecutionMerger.getClubbedMsgsMap().put(cliOrdId,Mockito.mock(Message.class));
        exchangeExecutionMerger.processExecutionReport(msgMock);

        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).sendClubbedExecutionReport(cliOrdId);
        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).sendExecutionReport(any(Message.class));
    }

    @Test
    void sendExecutionReportAtException_Test() throws FieldNotFound {
        msgMock.setString(FixConstants.FIX_TAG_SES_IDENTIFIER,sessionId);
        exchangeExecutionMerger.sendExecutionReportAtException(msgMock);
        Mockito.verify(fixApplicationMock,Mockito.times(IConstants.CONSTANT_ONE_1)).populateIntermediateQueueData(any(Message.class),any(DFIXMessage.class));
        Mockito.verify(fixApplicationMock,Mockito.times(IConstants.CONSTANT_ONE_1)).populateHAData(any(Message.class),any(DFIXMessage.class));
        Assertions.assertFalse(msgMock.isSetField(FixConstants.FIX_TAG_SES_IDENTIFIER));
    }

    @Test
    void clubAndStoreExecutionReport_Test() throws FieldNotFound {
        Message clubMessage = Mockito.spy(Message.class);
        clubMessage.setString(ClOrdID.FIELD, cliOrdId);
        clubMessage.setDouble(LastPx.FIELD,35.5);
        clubMessage.setInt(LastShares.FIELD,50);
        msgMock.setString(ExecID.FIELD,"1234567");


        exchangeExecutionMerger.clubAndStoreExecutionReport(cliOrdId,clubMessage,msgMock);
        Assertions.assertTrue(exchangeExecutionMerger.getPreviousMsgsMap().containsKey(cliOrdId) && exchangeExecutionMerger.getPreviousMsgsMap().get(cliOrdId).contains(msgMock));
        final Message newClubbedMsg = exchangeExecutionMerger.getClubbedMsgsMap().get(cliOrdId);
        Assertions.assertNotNull(newClubbedMsg);
        Assertions.assertEquals(IConstants.CONSTANT_ONE_FIFTY,newClubbedMsg.getInt(LastShares.FIELD));
    }


    @Test
    void sendClubbedExecutionReport_Test() throws FieldNotFound {
        Message clubMessage = Mockito.spy(Message.class);
        clubMessage.setString(ExecID.FIELD,"101");
        clubMessage.setString(FixConstants.FIX_TAG_SES_IDENTIFIER,sessionId);
        exchangeExecutionMerger.getClubbedMsgsMap().put(cliOrdId, clubMessage);
        exchangeExecutionMerger.getPreviousMsgsMap().put(cliOrdId, Mockito.spy(List.class));
        Mockito.doNothing().when(exchangeExecutionMerger).sendDfixMessage(clubMessage,sessionId);
        Mockito.doNothing().when(exchangeExecutionMerger).sendPreviousMsgList(any(List.class),anyString() ,anyString());
        exchangeExecutionMerger.sendClubbedExecutionReport(cliOrdId);
        Assertions.assertFalse(clubMessage.isSetField(FixConstants.FIX_TAG_SES_IDENTIFIER));
        Assertions.assertFalse(exchangeExecutionMerger.getClubbedMsgsMap().containsKey(cliOrdId));
        Assertions.assertFalse(exchangeExecutionMerger.getPreviousMsgsMap().containsKey(cliOrdId));
        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).sendDfixMessage(clubMessage,sessionId);
        Mockito.verify(exchangeExecutionMerger,Mockito.times(IConstants.CONSTANT_ONE_1)).sendPreviousMsgList(any(List.class),anyString() ,anyString());
    }

    @Test
    void storeExecutionReport_Test(){
        exchangeExecutionMerger.getClubbedMsgsMap().clear();
        exchangeExecutionMerger.getPreviousMsgsMap().clear();
        exchangeExecutionMerger.storeExecutionReport(cliOrdId,msgMock);
        Assertions.assertTrue(exchangeExecutionMerger.getPreviousMsgsMap().containsKey(cliOrdId));
        Assertions.assertTrue(exchangeExecutionMerger.getPreviousMsgsMap().get(cliOrdId).contains(msgMock));
    }

    @Test
    void sendExecutionReport_Test() throws FieldNotFound {
        msgMock.setString(FixConstants.FIX_TAG_SES_IDENTIFIER, sessionId);
        Mockito.doNothing().when(exchangeExecutionMerger).sendDfixMessage(msgMock, sessionId);
        exchangeExecutionMerger.sendExecutionReport(msgMock);
        Assertions.assertFalse(msgMock.isSetField(FixConstants.FIX_TAG_SES_IDENTIFIER));
    }

    @Test
    void sendDfixMessage_Test() throws FieldNotFound {
        exchangeExecutionMerger.sendDfixMessage(msgMock, sessionId);
        Mockito.verify(fixApplicationMock, Mockito.times(IConstants.CONSTANT_ONE_1)).populateIntermediateQueueData(any(Message.class), any(DFIXMessage.class));
        Mockito.verify(fixApplicationMock, Mockito.times(IConstants.CONSTANT_ONE_1)).populateHAData(any(Message.class), any(DFIXMessage.class));
        Mockito.verify(fromExchangeQueueMock, Mockito.times(IConstants.CONSTANT_ONE_1)).addMsg(any(DFIXMessage.class));
    }

    @Test
    void sendPreviousMsgList_Test() throws FieldNotFound {
        List<Message> previousMsgsList = new ArrayList<>();
        previousMsgsList.add(msgMock);

        exchangeExecutionMerger.sendPreviousMsgList(previousMsgsList,"101", sessionId);
        Assertions.assertTrue(previousMsgsList.isEmpty());
    }

}
