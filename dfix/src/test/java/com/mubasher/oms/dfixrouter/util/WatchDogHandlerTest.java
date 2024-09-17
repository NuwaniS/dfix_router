package com.mubasher.oms.dfixrouter.util;

import com.dfn.watchdog.agent.WatchdogAgent;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import quickfix.Message;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.*;

/**
 * Created by Nilaan L on 7/23/2024.
 */
class WatchDogHandlerTest {

    static MockedStatic<Settings> settingsMockedStatic;
    static MockedStatic<WatchDogHandler> watchDogHandlerMockedStatic;
    static MockedStatic<WatchdogAgent> watchdogAgentMockedStatic;

    @BeforeAll
    static void setUp() {
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        watchDogHandlerMockedStatic = Mockito.mockStatic(WatchDogHandler.class);
        watchdogAgentMockedStatic = Mockito.mockStatic(WatchdogAgent.class);
    }

    @AfterAll
    static void tearDown() {
        settingsMockedStatic.close();
        watchDogHandlerMockedStatic.close();
        watchdogAgentMockedStatic.close();
    }

    @Test
    void runWatchdogAgent_Test() throws NoSuchFieldException, IllegalAccessException {
        WatchdogAgent watchdogAgentMock = Mockito.spy(WatchdogAgent.INSTANCE);
        Mockito.doReturn(null).when(watchdogAgentMock).run();

        watchDogHandlerMockedStatic.when(WatchDogHandler::runWatchdogAgent).thenCallRealMethod();
        settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.FALCON_IP)).thenReturn("127.0.0.1");
        settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.FALCON_PORT)).thenReturn("127");
        settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.FALCON_IP_SECONDARY)).thenReturn("127.0.0.2");
        settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.FALCON_PORT_SECONDARY)).thenReturn("127");
        settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.FALCON_AUTHENTICATION_NODE_CATEGORY)).thenReturn("DFIX1");
        settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.FALCON_AUTHENTICATION_NODE_PASSWORD)).thenReturn("password");
        settingsMockedStatic.when(() -> Settings.getProperty(IConstants.SETTING_DFIX_ID)).thenReturn(IConstants.STRING_1);
        watchDogHandlerMockedStatic.when(WatchDogHandler::getWatchdogAgent).thenReturn(watchdogAgentMock);

        WatchDogHandler.runWatchdogAgent();
        Mockito.verify(watchdogAgentMock, Mockito.times(1)).run();
        Assertions.assertNotNull(TestUtils.changeFieldAccessibility(WatchDogHandler.class, "agentCallbackListener", true).get(null));
    }

    @Test
    void getAppServerId() throws NoSuchFieldException, IllegalAccessException {
        watchDogHandlerMockedStatic.when(()->WatchDogHandler.getAppServerId(any(Message.class))).thenCallRealMethod();
        watchDogHandlerMockedStatic.when(()->WatchDogHandler.getAppServerId(anyString())).thenCallRealMethod();
        AgentCallbackListenerDfix agentCallbackListener = Mockito.mock(AgentCallbackListenerDfix.class);
        Mockito.when(agentCallbackListener.next(anyInt())).thenReturn((short) IConstants.CONSTANT_ONE_1);
        settingsMockedStatic.when(() -> Settings.getHostIdForOmsId(IConstants.CONSTANT_ONE_1)).thenReturn(IConstants.CONSTANT_ZERO_0);
        final Field agentCallbackListenerField = TestUtils.changeFieldAccessibility(WatchDogHandler.class, "agentCallbackListener", true);
        agentCallbackListenerField.set(null, agentCallbackListener);
        Message message = new Message();
        message.setString(FixConstants.FIX_TAG_10008, IConstants.STRING_1);
        int appServerId = WatchDogHandler.getAppServerId(message);
        Assertions.assertEquals(IConstants.CONSTANT_ZERO_0, appServerId);

        //using message string
        appServerId = WatchDogHandler.getAppServerId(message.toString());
        Assertions.assertEquals(IConstants.CONSTANT_ZERO_0, appServerId);
    }

}
