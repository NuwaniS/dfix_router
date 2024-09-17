package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor;

import com.mubasher.oms.dfixrouter.beans.TradingMarketBean;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.exchange.ToExchangeQueue;
import com.mubasher.oms.dfixrouter.server.fix.FIXApplication;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import com.mubasher.oms.dfixrouter.util.stores.TradingMarketStore;
import com.objectspace.jgl.Queue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.*;


/**
 * Created by Nilaan L on 5/16/2024.
 */
class ExchangeMessageProcessorFactoryTest {

    @ParameterizedTest
    @ValueSource(strings = {"MSM", "TDWL", "KSE"})
    void processMessageKnownExchangeTest(String exchange) {
        boolean isSend = ExchangeMessageProcessorFactory.processMessage(exchange,"TestMessage");
        Assertions.assertTrue(isSend);
    }

    @Test
    void processMessage_ExecutionReport_KSE_Test() {
        try(MockedStatic<TradingMarketStore> mockedTradingMarketStore = Mockito.mockStatic(TradingMarketStore.class)) {
            TradingMarketBean tradingMarketBean = Mockito.mock(TradingMarketBean.class);
            Mockito.when(tradingMarketBean.getTradingSessionId()).thenReturn(FixConstants.FIX_VALUE_336_ACCEPTANCE);
            mockedTradingMarketStore.when(()->TradingMarketStore.getTradingMarket("KSE", TradingSessionID.AFTERNOON )).thenReturn(tradingMarketBean);

            Message msg = new Message();
            msg.getHeader().setString(MsgType.FIELD, MsgType.EXECUTION_REPORT);
            msg.setChar(ExecType.FIELD,ExecType.REPLACED);
            msg.setChar(TimeInForce.FIELD,TimeInForce.GOOD_TILL_DATE);
            msg.setString(TradingSessionID.FIELD, TradingSessionID.AFTERNOON);
            msg.setChar(OrdStatus.FIELD, FixConstants.FIX_VALUE_39_ORDER_UNPLACED);
            boolean isSend = ExchangeMessageProcessorFactory.processMessage("KSE", msg.toString());
            Assertions.assertFalse(isSend);
        }
    }

    @Test
    void processMessage_TradingSessionStatus_KSE_Test() {
            Message msg = new Message();
            msg.getHeader().setString(MsgType.FIELD, MsgType.TRADING_SESSION_STATUS);
            msg.setChar(ExecType.FIELD,ExecType.REPLACED);
            msg.setString(TradingSessionID.FIELD, FixConstants.FIX_VALUE_336_ENQUIRY);
            msg.setString(MarketSegmentID.FIELD, "REG");
            msg.setString(340, "100");
            msg.setString(1151, "1151");
            boolean isSend = ExchangeMessageProcessorFactory.processMessage("KSE", msg.toString());
            Assertions.assertTrue(isSend);
            Assertions.assertNotNull(TradingMarketStore.getTradingMarket("KSE", "REG"));
    }

    @Test
    void processMessageUnknownExchangeTest() {
        boolean isSend = ExchangeMessageProcessorFactory.processMessage("UNK","TestMessage");
        Assertions.assertTrue(isSend);
    }


    @ParameterizedTest
    @ValueSource(strings = {"MSM", "TDWL", "KSE"})
    void isAllowedParallelProcessing(String exchange) throws FieldNotFound {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD,MsgType.EXECUTION_REPORT);
        msg.setChar(OrdStatus.FIELD,OrdStatus.NEW);
        msg.setChar(ExecType.FIELD, ExecType.NEW);
        msg.setString(ClOrdID.FIELD, "Order_1" );

        final boolean allowedParallelProcessing = ExchangeMessageProcessorFactory.isAllowedParallelProcessing(msg, exchange);
        switch (exchange) {
            case "TDWL":
                Assertions.assertFalse(allowedParallelProcessing);
                break;
            case "MSM":
            case "KSE":
                Assertions.assertTrue(allowedParallelProcessing);
                break;
            default:
                Assertions.fail("Invalid exchange type: " + exchange);
        }

    }

    @ParameterizedTest
    @ValueSource(strings = {"MSM", "TDWL", "KSE"})
    void regenLogOnMsg(String exchange) throws NoSuchFieldException, IllegalAccessException {
        try(MockedStatic<FIXClient> fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
            MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class)) {
            FIXClient fixClientMock = Mockito.mock(FIXClient.class);
            FIXApplication fixApplication = Mockito.mock(FIXApplication.class);
            ToExchangeQueue toExchangeQueue = Mockito.spy(ToExchangeQueue.class);

            fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClientMock);
            Mockito.when(fixClientMock.getApplication()).thenReturn(fixApplication);

            dfixRouterManagerMockedStatic.when(DFIXRouterManager::getToExchangeQueue).thenReturn(toExchangeQueue);
            SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
            Message msg = new Message();
            msg.getHeader().setString(MsgType.FIELD,MsgType.LOGON);
            msg.setBoolean(ResetSeqNumFlag.FIELD,ResetSeqNumFlag.YES_RESET_SEQUENCE_NUMBERS);

            ExchangeMessageProcessorFactory.regenLogOnMsg(msg, sessionId, exchange);
            switch (exchange) {
                case "MSM":
                    Assertions.assertTrue(!((Queue)TestUtils.getPrivateField(toExchangeQueue,"queue")).isEmpty());
                    break;
                case "TDWL":
                case "KSE":
                    Assertions.assertTrue(((Queue)TestUtils.getPrivateField(toExchangeQueue,"queue")).isEmpty());
                    break;
                default:
                    Assertions.fail("Invalid exchange type: " + exchange);
            }

        }
    }
}
