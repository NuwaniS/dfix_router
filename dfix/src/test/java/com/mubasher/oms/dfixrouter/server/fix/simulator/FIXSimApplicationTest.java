package com.mubasher.oms.dfixrouter.server.fix.simulator;

import com.mubasher.oms.dfixrouter.constants.SimulatorSettings;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.*;
import quickfix.fix42.NewOrderSingle;

import java.io.File;
import java.io.FileInputStream;

class FIXSimApplicationTest {
    private static final String RUN_TIME_LOCATION = System.getProperty("base.dir") + "/src/main/external-resources";
    private static final String RUN_TIME_CONFIG_LOCATION = RUN_TIME_LOCATION + "/config/sim.cfg";
    SessionSettings sessionSettingsTest;
    FIXSimApplication fixSimApplication;
    @Spy
    FIXClient fixClientTest ;

    MockedStatic<FIXClient> fixClientMockedStatic ;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(FIXSimApplicationTest.class);
        fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
        loadSettings();
        fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClientTest);
        fixClientMockedStatic.when(FIXClient::getSimSettings).thenReturn(sessionSettingsTest);
    }

    @AfterEach
    public void tearDown() {
        fixClientMockedStatic.close();
    }

    private void loadSettings() throws Exception {
        if (sessionSettingsTest == null){
            final File file = new File(RUN_TIME_CONFIG_LOCATION);
            final FileInputStream in = new FileInputStream(file);
            sessionSettingsTest = new SessionSettings(in);
        }
        if (fixSimApplication == null){
            fixSimApplication = new FIXSimApplication(sessionSettingsTest);
        }
    }

    @Test
    void onCreate_FlowTest(){
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        fixSimApplication.onCreate(sessionId);
    }

    @Test
    void onLogon_FlowTest() {
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        fixSimApplication.onLogon(sessionId);
    }

    @Test
    void onLogout_FlowTest(){
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        fixSimApplication.onLogout(sessionId);
    }

    @Test
    void toAdmin_FlowTest(){
        Message message = new Message();
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        fixSimApplication.toAdmin(message, sessionId);
    }

    @Test
    void fromAdmin_FlowTest() throws Exception {
        Message message = new Message();
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        fixSimApplication.fromAdmin(message, sessionId);
    }

    @Test
    void toApp_FlowTest() throws Exception {
        Message message = new Message();
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        fixSimApplication.toApp(message, sessionId);
    }

    @Test
    void fromApp_FlowTest() throws Exception {
        int ordQty = 100;
        NewOrderSingle message = new NewOrderSingle();
        message.setString(Account.FIELD, "1");
        message.setString(ClOrdID.FIELD, "1");
        message.setInt(OrderQty.FIELD, ordQty);
        message.setString(Price.FIELD, "10.5");
        message.setString(OrdType.FIELD, "1");
        message.setString(Side.FIELD, "1");
        message.setString(Symbol.FIELD, "1010");
        SessionID sessionId = new SessionID("FIX.4.2","TDWL","SLGM0");
        fixSimApplication.fromApp(message, sessionId);
    }

    @Test
    void rejectOrder_FlowTest() throws Exception {
        int ordQty = 100;
        try (MockedStatic<Settings> settingsMockedStatic  =Mockito.mockStatic(Settings.class)) {
            settingsMockedStatic.when(()->Settings.getInt(SimulatorSettings.REJECT_QTY)).thenReturn(ordQty);
            NewOrderSingle message = new NewOrderSingle();
            message.setString(Account.FIELD, "1");
            message.setString(ClOrdID.FIELD, "1");
            message.setInt(OrderQty.FIELD, ordQty);
            message.setString(Price.FIELD, "10.5");
            message.setString(OrdType.FIELD, "1");
            message.setString(Side.FIELD, "1");
            message.setString(Symbol.FIELD, "1010");
            SessionID sessionId = new SessionID("FIX.4.2", "TDWL", "SLGM0");
            fixSimApplication.fromApp(message, sessionId);
        }
    }

    @Test
    void stopOrderHandler_FlowTest() {
        try (MockedStatic<OrderHandler> orderHandlerMockedStatic = Mockito.mockStatic(OrderHandler.class)) {
            fixSimApplication.stopOrderHandler();
            orderHandlerMockedStatic.verify(OrderHandler::stop, Mockito.times(1));
        }
    }
}
