package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.concurrent;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.InternalBean;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.exchange.FromExchangeQueue;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.ExchangeMessageProcessorFactory;
import com.mubasher.oms.dfixrouter.server.fix.FIXApplication;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.fix42.ExecutionReport;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Created by Nilaan L on 7/15/2024.
 */
class ConcurrentProcessorTest {

    @AfterAll
    static void tearDown() throws NoSuchFieldException, IllegalAccessException {
        // reset singleton
        TestUtils.changeFieldAccessibility(FIXClient.class,"fixClient",true).set(null,null);
    }

    @Test
    void messageQHandler_Flow_Test() throws Exception {
        try (MockedStatic<Settings> settingsMockedStatic = Mockito.mockStatic(Settings.class);
             MockedStatic<FIXClient> fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
             MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);
             MockedStatic<ExchangeMessageProcessorFactory> exchangeMessageProcessorFactoryMockedStatic = Mockito.mockStatic(ExchangeMessageProcessorFactory.class);) {
            //mock static behaviours
            settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.UNPLACED_PROCESS_START_DELAY)).thenReturn(String.valueOf(IConstants.CONSTANT_FOUR_THOUSAND_4000));
            settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.UNPLACED_PROCESSING_INTERVAL)).thenReturn(String.valueOf(IConstants.CONSTANT_TWO_THOUSAND_2000));
            settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.UNPLACED_PROCESS_START_WAITING_INTERVAL)).thenReturn(String.valueOf(IConstants.CONSTANT_FOUR_THOUSAND_4000));
            settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.UNPLACED_PROCESSING_SPLIT_SIZE)).thenReturn(String.valueOf(IConstants.CONSTANT_TEN_10));
            exchangeMessageProcessorFactoryMockedStatic.when(()->ExchangeMessageProcessorFactory.processMessage(anyString(),anyString())).thenReturn(IConstants.CONSTANT_TRUE);

            SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
            Map<SessionID, SessionSettings> settings = new HashMap<>();
            Message message = new ExecutionReport();
            message.setString(1002,"TestModifyTag");

            FIXApplication fixApplicationMock = Mockito.mock(FIXApplication.class);
            FIXClient fixClientMock = Mockito.mock(FIXClient.class);
            FromExchangeQueue fromExchangeQueue =  Mockito.mock(FromExchangeQueue.class);

            final SessionSettings sessionSettingMock = Mockito.mock(SessionSettings.class);
            Properties properties = new Properties();
            properties.setProperty(IConstants.SETTING_TAG_MODIFICATION,IConstants.SETTING_YES);
            properties.setProperty("Tag1002","ModifyTag");
            Mockito.when(sessionSettingMock.getSessionProperties(sessionId,IConstants.CONSTANT_TRUE)).thenReturn(properties);
            settings.put(sessionId, sessionSettingMock);

            fixClientMockedStatic.when(()->FIXClient.getSettings()).thenReturn(settings);
            dfixRouterManagerMockedStatic.when(DFIXRouterManager::getFromExchangeQueue).thenReturn(fromExchangeQueue);
            fixClientMockedStatic.when(()->FIXClient.getFIXClient()).thenReturn(fixClientMock);
            Mockito.when(fixClientMock.getApplication()).thenReturn(fixApplicationMock);
            Mockito.doReturn("TDWL").when(fixApplicationMock).getSessionIdentifier(sessionId);

            InternalBean internalBeanMock = Mockito.mock(InternalBean.class);
            Mockito.when(internalBeanMock.getMessage()).thenReturn(message);
            Mockito.when(internalBeanMock.getSessionId()).thenReturn(sessionId);

            MessageQHandler messageQHandler = Mockito.mock(MessageQHandler.class);
            final LinkedBlockingQueue<InternalBean> msgQueue = new LinkedBlockingQueue<>();
            Mockito.when(messageQHandler.getMessageQueue()).thenReturn(msgQueue);
            msgQueue.add(internalBeanMock);
            final ConcurrentProcessor concurrentProcessor = Mockito.spy(new ConcurrentProcessor(messageQHandler));

            //TradingMarketStore-> Enquiry && startUpDelay True (process StartupDelay )
            TestUtils.invokePrivateMethod(concurrentProcessor,"handleNonEnquiryState",new Class[]{});
            Assertions.assertFalse(ConcurrentProcessor.getIsStartupDelay());

            //TradingMarketStore-> Enquiry && startUpDelay False ( processMessage )
            TestUtils.invokePrivateMethod(concurrentProcessor,"handleNonEnquiryState",new Class[]{});
            Mockito.verify(fromExchangeQueue,Mockito.times(IConstants.CONSTANT_ONE_1)).addMsg(any(DFIXMessage.class));
            Assertions.assertEquals(IConstants.CONSTANT_ONE_1, ConcurrentProcessor.getProcessCount());
            Assertions.assertEquals("Test",message.getString(1002));

            //TradingMarketStore-> non Enquiry
            TestUtils.invokePrivateMethod(concurrentProcessor,"handleEnquiryState",new Class[]{});
            Assertions.assertTrue(ConcurrentProcessor.getIsStartupDelay());
        }

    }
}
