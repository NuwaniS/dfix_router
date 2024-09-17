package com.mubasher.oms.dfixrouter.server.fix.simulator;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SimulatorSettings;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.*;
import quickfix.fix44.TradeCaptureReportAck;

/**
 * Created by Nilaan L on 7/23/2024.
 */
class NegDealExecutorTest {

    SessionID sessionID;
    static MockedStatic<Settings> settingsMockedStatic;

    @BeforeAll
    static void setup() {
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        settingsMockedStatic.when(() -> Settings.getInt(SimulatorSettings.REJECT_QTY)).thenReturn(IConstants.CONSTANT_FIVE_5);
    }

    @AfterAll
    static void tearDown() {
        settingsMockedStatic.close();
    }

    @BeforeEach
    void setUp() {
        sessionID = new SessionID("FIX.4.2", "SLGM0", "TDWL");
    }

    @Test
    void sendAcknowledgement_Test() throws Exception {
        Message message = new Message();
        message.setInt(LastShares.FIELD, IConstants.CONSTANT_FIVE_5);
        message.setString(TradeReportID.FIELD, "11");

        NegDealExecutor negDealExecutor = new NegDealExecutor(message, sessionID);
        final Message reportAck = (Message) TestUtils.invokePrivateMethod(negDealExecutor, "sendAcknowledgement", new Class[]{int.class}, TradeReportType.SUBMIT);
        Assertions.assertEquals(TradeCaptureReportAck.MSGTYPE, reportAck.getHeader().getString(MsgType.FIELD));
        Assertions.assertEquals("11", reportAck.getString(TradeReportID.FIELD));
        Assertions.assertEquals(TradeReportTransType.NEW, reportAck.getInt(TradeReportTransType.FIELD));
        Assertions.assertEquals(TradeReportType.SUBMIT, reportAck.getInt(TradeReportType.FIELD));
        Assertions.assertEquals(TrdRptStatus.REJECTED, reportAck.getInt(TrdRptStatus.FIELD));
        Assertions.assertTrue(reportAck.isSetField(TradeReportRefID.FIELD));
        Assertions.assertTrue(reportAck.isSetField(ExecID.FIELD));
    }
}
