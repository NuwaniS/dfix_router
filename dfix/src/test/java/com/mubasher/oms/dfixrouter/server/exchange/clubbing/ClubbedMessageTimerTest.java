package com.mubasher.oms.dfixrouter.server.exchange.clubbing;

import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.ExecID;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;

/**
 * Created by Nilaan L on 7/30/2024.
 */
class ClubbedMessageTimerTest {

    @Test
    void ClubbedMessageTimer_Test() throws FieldNotFound {
        try (MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);
             MockedStatic<Settings> settingsMockedStatic = Mockito.mockStatic(Settings.class)) {
            DFIXRouterManager dfixRouterManagerMock = Mockito.mock(DFIXRouterManager.class);
            ExchangeExecutionMerger exchangeExecutionMergerMock = Mockito.spy(ExchangeExecutionMerger.class);

            settingsMockedStatic.when(Settings::getClubbingTimeOut).thenReturn(1000L);
            dfixRouterManagerMockedStatic.when(DFIXRouterManager::getInstance).thenReturn(dfixRouterManagerMock);

            Mockito.when(dfixRouterManagerMock.getExchangeExecutionMerger()).thenReturn(exchangeExecutionMergerMock);
            Mockito.when(dfixRouterManagerMock.isStarted()).thenReturn(IConstants.CONSTANT_TRUE);
            Message clubbedMessage = new Message();
            clubbedMessage.setString(ExecID.FIELD, "123");
            clubbedMessage.setString(FixConstants.FIX_TAG_SES_IDENTIFIER, "TDWL-CL");
            exchangeExecutionMergerMock.getClubbedMsgsMap().put("1", clubbedMessage);
            exchangeExecutionMergerMock.getPreviousMsgsMap().put("1", new ArrayList<>());
            Mockito.doNothing().when(exchangeExecutionMergerMock).sendDfixMessage(any(Message.class), anyString());
            Mockito.doNothing().when(exchangeExecutionMergerMock).sendPreviousMsgList(any(List.class), anyString(), anyString());
            ClubbedMessageTimer clubbedMessageTimer = new ClubbedMessageTimer();
            dfixRouterManagerMockedStatic.when(() -> DFIXRouterManager.sleepThread(Mockito.anyLong())).then(
                    invocation -> {
                        Mockito.doReturn(IConstants.CONSTANT_FALSE).when(dfixRouterManagerMock).isStarted();
                        return null;
                    });

            clubbedMessageTimer.init();
            clubbedMessageTimer.run();
            Assertions.assertTrue(exchangeExecutionMergerMock.getClubbedMsgsMap().isEmpty());
            Assertions.assertFalse(clubbedMessageTimer.isAlive());

        }
    }
}
