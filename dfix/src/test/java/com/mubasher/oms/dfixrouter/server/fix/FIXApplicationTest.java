package com.mubasher.oms.dfixrouter.server.fix;

import com.dfn.watchdog.commons.State;
import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.admin.AdminManager;
import com.mubasher.oms.dfixrouter.server.admin.UIAdminServer;
import com.mubasher.oms.dfixrouter.server.exchange.FromExchangeQueue;
import com.mubasher.oms.dfixrouter.server.exchange.ToExchangeQueue;
import com.mubasher.oms.dfixrouter.server.exchange.clubbing.ExchangeExecutionMerger;
import com.mubasher.oms.dfixrouter.server.fix.flowcontrol.FlowControlStatus;
import com.mubasher.oms.dfixrouter.server.fix.flowcontrol.FlowController;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import com.mubasher.oms.dfixrouter.util.WatchDogHandler;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import quickfix.Group;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.*;
import quickfix.fix42.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by isharaw on 9/20/2017.
 */
class FIXApplicationTest {
    FIXApplication fixApplicationTest;
    @Spy
    DFIXRouterManager dfixRouterManagerTest;
    FromExchangeQueue fromExchangeQueueTest;
    @Spy
    ToExchangeQueue toExchangeQueueTest;
    @Spy
    FIXClient fixClientTest;
    @Spy
    SessionSettings sessionSettingsTest;
    @Spy
    ExchangeExecutionMerger exchangeExecutionMergerTest;

    private MockedStatic<FIXClient> fixClientMockedStatic ;
    private MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic ;
    private MockedStatic<Settings> settingsMockedStatic ;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        dfixRouterManagerTest.setExchangeExecutionMerger(exchangeExecutionMergerTest);

        fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
        dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);
        settingsMockedStatic = Mockito.mockStatic(Settings.class);

        fixApplicationTest = Mockito.spy(FIXApplication.class);
        fromExchangeQueueTest = Mockito.spy(new FromExchangeQueue());
        fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClientTest);
        Map map = Mockito.mock(HashMap.class);
        fixClientMockedStatic.when(FIXClient::getSettings).thenReturn(map);
        Mockito.when(map.get(any())).thenReturn(sessionSettingsTest);
        dfixRouterManagerMockedStatic.when(()->DFIXRouterManager.getToExchangeQueue()).thenReturn(toExchangeQueueTest);
        dfixRouterManagerMockedStatic.when(DFIXRouterManager::getFromExchangeQueue).thenReturn(fromExchangeQueueTest);
        dfixRouterManagerMockedStatic.when(DFIXRouterManager::getInstance).thenReturn(dfixRouterManagerTest);
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.TRADING_SESSIONS_AUTO_CONNECT)).thenReturn(IConstants.SETTING_NO);
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG)).thenReturn(IConstants.SETTING_NO);
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.SETTING_DFIX_ID)).thenReturn("1");
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.EXCHANGE_LEVEL_GROUPING)).thenReturn(IConstants.SETTING_NO);
    }
    @AfterEach
    public void tearDown(){
        fixClientMockedStatic.close();
        dfixRouterManagerMockedStatic.close();
        settingsMockedStatic.close();
    }

    @Test
    void onCreateTest(){
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Mockito.doNothing().when(fixApplicationTest).populateSessionNoData(sessionId);
        Mockito.doReturn("TDWL").when(fixApplicationTest).getSessionIdentifier(sessionId);
        fixApplicationTest.onCreate(sessionId);
        Mockito.verify(fixApplicationTest, Mockito.times(1)).populateSessionNoData(sessionId);
        Assertions.assertTrue(true);
    }

    @Test
    void onLogon_FlowTest() throws Exception{
        try (MockedStatic<UIAdminServer> uiAdminServerMockedStatic = Mockito.mockStatic(UIAdminServer.class)) {
            SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
            Mockito.doReturn("TDWL").when(fixClientTest).getSessionProperty(sessionId, IConstants.SESSION_IDENTIFIER);
            fixApplicationTest.onLogon(sessionId);
            Mockito.verify(fromExchangeQueueTest, Mockito.times(1)).addMsg(any(DFIXMessage.class));
            uiAdminServerMockedStatic.verify(()->UIAdminServer.sendToAdminClients(IConstants.ALL_SESSIONS, AdminManager.getInstance().sendSessionList()),Mockito.times(1));
            Mockito.verify(fixApplicationTest, Mockito.times(1)).sendLinkStatusConnected(Mockito.anyString(), any(SessionID.class));
        }
    }

    @Test
    void onLogout_FlowTest() throws Exception {
        try (MockedStatic<UIAdminServer> uiAdminServerMockedStatic = Mockito.mockStatic(UIAdminServer.class)) {
            SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
            Mockito.doReturn("TDWL").when(fixClientTest).getSessionProperty(sessionId, IConstants.SESSION_IDENTIFIER);
            Mockito.doNothing().when(fromExchangeQueueTest).addMsg(any(DFIXMessage.class));
            fixApplicationTest.onLogout(sessionId);
            Mockito.verify(fromExchangeQueueTest, Mockito.times(1)).addMsg(any(DFIXMessage.class));
            uiAdminServerMockedStatic.verify(()->UIAdminServer.sendToAdminClients("TDWL", IConstants.STRING_SESSION_DISCONNECTED),Mockito.times(1));
            Mockito.verify(fixApplicationTest, Mockito.times(1)).sendLinkStatusDisconnected(Mockito.anyString(), any(SessionID.class));
        }
    }

    @Test
    void toAdmin_LogOnMessageAddLogOnFieldTest() throws Exception {
        try (MockedStatic<UIAdminServer> uiAdminServerMockedStatic = Mockito.mockStatic(UIAdminServer.class)) {
            SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
            Message message = new Logon();
            Mockito.doReturn("TDWL").when(fixClientTest).getSessionProperty(sessionId, IConstants.SESSION_IDENTIFIER);
            Properties prop = new Properties();
            prop.put("LogonUserDefined", "96=mubasher|101=1234");
            Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId, true);
            fixApplicationTest.toAdmin(message, sessionId);
            Assertions.assertEquals("mubasher", message.getString(96));
            Assertions.assertEquals("1234", message.getString(101));
            uiAdminServerMockedStatic.verify(()->UIAdminServer.sendToAdminClients("TDWL", IConstants.SESSION_SENT_LOGON),Mockito.times(1));
        }
    }
    @Test
    void toAdmin_LogOnMessageScheduledPWReset_PWFileNotExistTest() throws Exception {
        //no pw file exist

        try (MockedStatic<UIAdminServer> uiAdminServerMockedStatic = Mockito.mockStatic(UIAdminServer.class)) {
            //Arrange
            Message message = new Logon();
            SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");

            Mockito.doReturn("Y").when(fixClientTest).getSessionProperty(sessionId, IConstants.SETTING_TAG_IS_SCH_PASS_RESET);
            Mockito.doReturn("TDWL").when(fixClientTest).getSessionProperty(sessionId, IConstants.SESSION_IDENTIFIER);
            Mockito.doReturn(new Properties()).when(sessionSettingsTest).getSessionProperties(sessionId, true);

            final String expectedPw = (String) TestUtils.invokePrivateMethod(fixApplicationTest, "getNewPassword", new Class<?>[]{SimpleDateFormat.class, String.class},new SimpleDateFormat("yyMMdd"), "TDWL");
            File file = new File("./quick_fix/output/TDWL.txt");

            //Act
            fixApplicationTest.toAdmin(message, sessionId);
            String currPass;
            try (FileReader fileReader = new FileReader(file)) {
                BufferedReader in = new BufferedReader(fileReader);
                currPass = in.readLine();
            }

            //Assert
            Assertions.assertEquals("SLGM0", message.getString(Username.FIELD));
            Assertions.assertEquals(expectedPw, message.getString(Password.FIELD));
            Assertions.assertEquals(expectedPw, message.getString(NewPassword.FIELD));

            Assertions.assertTrue(Files.deleteIfExists(file.toPath()));
            Assertions.assertEquals(expectedPw,currPass);
            uiAdminServerMockedStatic.verify(()->UIAdminServer.sendToAdminClients("TDWL", IConstants.SESSION_SENT_LOGON),Mockito.times(1));
        }
    }

    @Test
    void toAdmin_LogOnMessageScheduledPWReset_PWFileExist_Test() throws Exception {
        //PW file already exist and pw is expired

        try (MockedStatic<UIAdminServer> uiAdminServerMockedStatic = Mockito.mockStatic(UIAdminServer.class)) {
            //Arrange
            Message message = new Logon();
            SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");

            Mockito.doReturn("Y").when(fixClientTest).getSessionProperty(sessionId, IConstants.SETTING_TAG_IS_SCH_PASS_RESET);
            Mockito.doReturn("TDWL").when(fixClientTest).getSessionProperty(sessionId, IConstants.SESSION_IDENTIFIER);
            Mockito.doReturn(new Properties()).when(sessionSettingsTest).getSessionProperties(sessionId, true);

            final String oldPw = "Yu@240601!";
            final String expectedPw = (String) TestUtils.invokePrivateMethod(fixApplicationTest, "getNewPassword", new Class<?>[]{SimpleDateFormat.class, String.class},new SimpleDateFormat("yyMMdd"), "TDWL");
            File file = new File("./quick_fix/output/TDWL.txt");
            Files.deleteIfExists(file.toPath());

            if(!Files.exists(file.getParentFile().toPath(), LinkOption.NOFOLLOW_LINKS)){
                Files.createDirectories(file.getParentFile().toPath());
            }
            try (FileWriter fileWriter = new FileWriter(file)){
                fileWriter.write(oldPw);
            } catch (Exception e) {
                Assertions.fail();
            }
            //Act
            fixApplicationTest.toAdmin(message, sessionId);
            String currPass;
            try (FileReader fileReader = new FileReader(file)) {
                BufferedReader in = new BufferedReader(fileReader);
                currPass = in.readLine();
            }

            //Assert
            Assertions.assertEquals("SLGM0", message.getString(Username.FIELD));
            Assertions.assertEquals(oldPw, message.getString(Password.FIELD));
            Assertions.assertEquals(expectedPw, message.getString(NewPassword.FIELD));

            Assertions.assertTrue(Files.deleteIfExists(file.toPath()));
            Assertions.assertEquals(expectedPw,currPass);
            uiAdminServerMockedStatic.verify(()->UIAdminServer.sendToAdminClients("TDWL", IConstants.SESSION_SENT_LOGON),Mockito.times(1));
        }
    }

    @Test
    void toAdmin_LogOnMessageNotAddLogOnFieldTest() throws Exception {
        try (MockedStatic<UIAdminServer> uiAdminServerMockedStatic = Mockito.mockStatic(UIAdminServer.class)) {

            SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
            Message message = new Logon();
            Mockito.doReturn("TDWL").when(fixClientTest).getSessionProperty(sessionId, IConstants.SESSION_IDENTIFIER);
            Properties prop = new Properties();
            Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId, true);
            fixApplicationTest.toAdmin(message, sessionId);
            uiAdminServerMockedStatic.verify(() -> UIAdminServer.sendToAdminClients("TDWL", IConstants.SESSION_SENT_LOGON), Mockito.times(1));
            Assertions.assertFalse(message.isSetField(96));
        }
    }

    @Test
    void toAdmin_IncorrectConfigurationTest() throws Exception {
        try (MockedStatic<UIAdminServer> uiAdminServerMockedStatic = Mockito.mockStatic(UIAdminServer.class)) {
            SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
            Message message = new Logon();
            Properties prop = new Properties();
            prop.put("LogonUserDefined", "96+mubasher,101+1234");
            Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId, true);
            fixApplicationTest.toAdmin(message, sessionId);
            Assertions.assertFalse(message.isSetField(96));
            uiAdminServerMockedStatic.verify(()->UIAdminServer.sendToAdminClients("TDWL", IConstants.SESSION_SENT_LOGON),Mockito.times(0));
        }
    }

    @Test
    void toAdmin_logOutMessageTest() {
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Logout();
        fixApplicationTest.toAdmin(message,sessionId);
        Assertions.assertTrue(true);
    }

    @Test
    void toAdmin_HeartBeatMessageTest() {
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Heartbeat();
        fixApplicationTest.toAdmin(message,sessionId);
        Assertions.assertTrue(true);
    }

//        @Test
    void fromAdmin_LogOnMessageResetSeqNumFlagNTest() throws Exception{
        try (MockedStatic<UIAdminServer> uiAdminServerMockedStatic = Mockito.mockStatic(UIAdminServer.class)) {
            SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
            Message message = new Logon();
            message.setString(141, "Y");
            Mockito.doReturn("TDWL").when(fixClientTest).getSessionProperty(sessionId, IConstants.SESSION_IDENTIFIER);
            Properties prop = new Properties();
            prop.put("LogonUserDefined", "96=mubasher|101=1234");
            Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId, true);
            Mockito.doNothing().when(toExchangeQueueTest).addMsg(Mockito.anyString());
            fixApplicationTest.fromAdmin(message, sessionId);
            Mockito.verify(toExchangeQueueTest, Mockito.times(1)).addMsg(Mockito.anyString());
        }
    }

    //    @Test
    void fromAdmin_LogOnMessageResetSeqNumFlag_for_N_Test() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Logon();
        message.setString(141,"N");
        fixApplicationTest.fromAdmin(message , sessionId);
        Assertions.assertTrue(true);
    }

    @Test
    void fromAdmin_LogOutMessageTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Logout();
        message.setString(Text.FIELD,"Test");
        fixApplicationTest.fromAdmin(message , sessionId);
        Assertions.assertTrue(true);
    }

    @Test
    void fromAdmin_HeartBeatMessageTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Heartbeat();
        fixApplicationTest.fromAdmin(message , sessionId);
        Assertions.assertTrue(true);
    }

    @Test
    void toApp_addTagModification_ClOrderIDTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Message();
        message.setString(11,"D");
        Properties prop = new Properties();
        prop.put("TagModification","Y");
        prop.put("Tag11","FIX");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals("FIXD", message.getString(11));
    }

    @Test
    void toApp_addTagModification_TradingAccountNoTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Message();
        message.setString(1,"X");
        Properties prop = new Properties();
        prop.put("TagModification","Y");
        prop.put("Tag1","FIX");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals("FIXX", message.getString(1));
    }

    @Test
    void toApp_addTagModification_SettingNotConfiguredTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Message();
        message.setString(1,"X");
        Properties prop = new Properties();
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals("X", message.getString(1));
    }

    @Test
    void toApp_addTagModification_SettingDisabledTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Message();
        message.setString(1,"X");
        Properties prop = new Properties();
        prop.put("TagModification","N");
        prop.put("Tag1","FIX");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals("X", message.getString(1));
    }

    @Test
    void toApp_changeTargetCompID_SettingConfiguredTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Message();
        message.getHeader().setString(56,"YYYY");
        Properties prop = new Properties();
        prop.put("AppMsgTargetCompID","XXXX");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals("XXXX", message.getHeader().getString(56));
    }

    @Test
    void toApp_changeTargetCompID_SettingNotConfiguredTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Message();
        message.getHeader().setString(56,"YYYY");
        Properties prop = new Properties();
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals("YYYY", message.getHeader().getString(56));
    }

    @Test
    void toApp_addCustomFields_SettingConfiguredTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Message();
        message.getHeader().setString(56,"YYYY");
        Properties prop = new Properties();
        prop.put("UserDefined","96=mubasher|101=1234");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals("mubasher", message.getString(96));
        Assertions.assertEquals("1234", message.getString(101));
    }

    @Test
    void toApp_addCustomFields_SettingNotConfiguredTest() throws Exception{
        Properties prop = new Properties();
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Message();
        message.getHeader().setString(56,"YYYY");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertFalse(message.isSetField(96));
        Assertions.assertFalse(message.isSetField(101));
    }

    @Test
    void toApp_addRepeatingGroup_NewOrderTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new NewOrderSingle();
        message.setString(9917,"1");
        message.setString(204,"0");
        message.setString(9947,"FIX_AM013");
        message.setString(439,"AMINAMIX");
        message.setString(1,"1234");
        Properties prop = new Properties();
        prop.put("RepeatingGrpTags","9917|204|439|1|9947");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals("0", message.getGroups(9917).get(0).getString(204));
        Assertions.assertEquals("FIX_AM013", message.getGroups(9917).get(0).getString(9947));
        Assertions.assertEquals("AMINAMIX", message.getGroups(9917).get(0).getString(439));
        Assertions.assertEquals("1234", message.getGroups(9917).get(0).getString(1));
    }

    @Test
    void toApp_addRepeatingGroup_AmendOrderTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new OrderCancelReplaceRequest();
        message.setString(9917,"1");
        message.setString(204,"0");
        message.setString(9947,"FIX_AM013");
        message.setString(439,"AMINAMIX");
        message.setString(1,"1234");
        Properties prop = new Properties();
        prop.put("RepeatingGrpTags","9917|204|439|1|9947");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals("0", message.getGroups(9917).get(0).getString(204));
        Assertions.assertEquals("FIX_AM013", message.getGroups(9917).get(0).getString(9947));
        Assertions.assertEquals("AMINAMIX", message.getGroups(9917).get(0).getString(439));
        Assertions.assertEquals("1234", message.getGroups(9917).get(0).getString(1));
    }

    @Test
    void toApp_addRepeatingGroup_SettingNotConfiguredTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new OrderCancelReplaceRequest();
        message.setString(9917,"1");
        message.setString(204,"0");
        message.setString(9947,"FIX_AM013");
        message.setString(439,"AMINAMIX");
        message.setString(1,"1234");
        Properties prop = new Properties();
        prop.put("AppMsgTargetCompID","9917|204|439|1|9947");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals(0, message.getGroups(9917).size());

    }

    void toApp_addRepeatingGroup_CancelOrderTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new OrderCancelRequest();
        message.setString(9917,"1");
        message.setString(204,"0");
        message.setString(9947,"FIX_AM013");
        message.setString(439,"AMINAMIX");
        message.setString(1,"1234");
        Properties prop = new Properties();
        prop.put("RepeatingGrpTags","9917|204|439|1|9947");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        fixApplicationTest.toApp(message,sessionId);
        Assertions.assertEquals(0, message.getGroups(9917).size());

    }

    @Test
    void fromApp_removeTagModification_ClOrderIDTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new NewOrderSingle();
        message.setString(11,"FIXD");
        Properties prop = new Properties();
        prop.put("TagModification","Y");
        prop.put("Tag11","FIX");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        Mockito.doNothing().when(fromExchangeQueueTest).addMsg(any(DFIXMessage.class));
        fixApplicationTest.fromApp(message,sessionId);
        Assertions.assertEquals("D", message.getString(11));
    }

    @Test
    void fromApp_removeTagModification_TradingAccountNoTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new NewOrderSingle();
        message.setString(1,"FIXX");
        Properties prop = new Properties();
        prop.put("TagModification","Y");
        prop.put("Tag1","FIX");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        Mockito.doNothing().when(fromExchangeQueueTest).addMsg(any(DFIXMessage.class));

        fixApplicationTest.fromApp(message,sessionId);
        Assertions.assertEquals("X", message.getString(1));
    }

    @Test
    void fromApp_removeTagModification_SettingDisabledTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new NewOrderSingle();
        message.setString(1,"FIXX");
        Properties prop = new Properties();
        prop.put("TagModification","N");
        prop.put("Tag1","FIX");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        Mockito.doNothing().when(fromExchangeQueueTest).addMsg(any(DFIXMessage.class));
        fixApplicationTest.fromApp(message,sessionId);
        Assertions.assertEquals("FIXX", message.getString(1));
    }

    @Test
    void fromApp_removeTagModification_SettingNotConfiguredTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new NewOrderSingle();
        message.setString(1,"FIXX");
        Properties prop = new Properties();
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        Mockito.doNothing().when(fromExchangeQueueTest).addMsg(any(DFIXMessage.class));

        fixApplicationTest.fromApp(message,sessionId);
        Assertions.assertEquals("FIXX", message.getString(1));
    }

    @Test
    void fromApp_FlowTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new NewOrderSingle();
        message.setString(1,"FIXX");
        Properties prop = new Properties();
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);
        Mockito.doNothing().when(fromExchangeQueueTest).sendMessageToAll(any(DFIXMessage.class));
        Mockito.doReturn("TDWL").when(fixApplicationTest).getSessionIdentifier(sessionId);
        fixApplicationTest.fromApp(message,sessionId);
        Mockito.verify(fromExchangeQueueTest, Mockito.times(1)).sendMessageToAll(any(DFIXMessage.class) );
    }

    @Test
    void canLogon_autoConnectONTest () {
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.TRADING_SESSIONS_AUTO_CONNECT)).thenReturn(IConstants.SETTING_YES);
        FIXApplication fixApplicationTest = Mockito.spy(FIXApplication.class);
        Assertions.assertTrue(fixApplicationTest.canLogon(sessionId));
    }

    @Test
    void canLogon_autoConnectOFF_OtherSessionTest() {
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        FIXApplication fixApplicationTest = Mockito.spy(FIXApplication.class);
        Assertions.assertFalse(fixApplicationTest.canLogon(sessionId));
    }

    @Test
    void isNotClubbingExecution_nullTest() {
        SessionID sessionId = null;
        Message message = null;
        Assertions.assertTrue(fixApplicationTest.isNotClubbingExecution(message, sessionId));
    }

    @Test
    void isNotClubbingExecution_failTest() {
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Message();
        message.setString(ClOrdID.FIELD, "1");
        Assertions.assertTrue(fixApplicationTest.isNotClubbingExecution(message, sessionId));
    }

    @Test
    void checkOrderStatusNew_passTest() throws Exception{
        ExecutionReport executionReport = new ExecutionReport();
        String clOrdId = "1";
        executionReport.setString(ClOrdID.FIELD, clOrdId);
        executionReport.setChar(OrdStatus.FIELD, OrdStatus.NEW);

        Method reflectedMethod = fixApplicationTest.getClass().getDeclaredMethod("checkOrderStatus",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        boolean isAddToClub = (boolean) reflectedMethod.invoke(fixApplicationTest,executionReport);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(isAddToClub, "New Order has to be added to the Clubbing.");
        Assertions.assertTrue(fixApplicationTest.getOrderLastExecutionTimeData().containsKey(clOrdId), "Order Last Execution Time data has to be populated with New Order");

        Method reflectedMethod1 = fixApplicationTest.getClass().getDeclaredMethod("resolveClubbingSettings",Message.class);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod1.invoke(fixApplicationTest , executionReport);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(exchangeExecutionMergerTest.getClOrdIdList().contains(clOrdId), "ClOrdId has to be added to Exchange Execution Merger ClOrdIdList");
    }

    @Test
    void checkOrderStatusFilled_passTest() throws Exception{
        ExecutionReport executionReport = new ExecutionReport();
        String clOrdId = "1";
        executionReport.setString(ClOrdID.FIELD, clOrdId);
        executionReport.setChar(OrdStatus.FIELD, OrdStatus.FILLED);

        Method reflectedMethod = fixApplicationTest.getClass().getDeclaredMethod("checkOrderStatus",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        boolean isAddToClub = (boolean) reflectedMethod.invoke(fixApplicationTest , executionReport);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(isAddToClub, "Filled Order has to be added to the Clubbing.");
        Assertions.assertFalse(fixApplicationTest.getOrderLastExecutionTimeData().containsKey(clOrdId), "Order Last Execution Time data has to be removed with Filled Order");

        Method reflectedMethod1 = fixApplicationTest.getClass().getDeclaredMethod("resolveClubbingSettings",Message.class);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod1.invoke(fixApplicationTest , executionReport);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(exchangeExecutionMergerTest.getClOrdIdList().contains(clOrdId), "ClOrdId has to be added to Exchange Execution Merger ClOrdIdList");
    }

    @Test
    void checkOrderStatusReplaced_passTest() throws Exception{
        ExecutionReport executionReport = new ExecutionReport();
        String clOrdId = "1";
        String origClOrdId = "2";
        executionReport.setString(ClOrdID.FIELD, clOrdId);
        executionReport.setChar(OrdStatus.FIELD, OrdStatus.REPLACED);
        executionReport.setString(OrigClOrdID.FIELD, origClOrdId);

        Method reflectedMethod = fixApplicationTest.getClass().getDeclaredMethod("checkOrderStatus",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        boolean isAddToClub = (boolean) reflectedMethod.invoke(fixApplicationTest , executionReport);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(isAddToClub, "Filled Order has to be added to the Clubbing.");
        Assertions.assertTrue(fixApplicationTest.getOrderLastExecutionTimeData().containsKey(clOrdId), "Order Last Execution Time data - ClOrdId has to be populated with Replaced Order");
        Assertions.assertFalse(fixApplicationTest.getOrderLastExecutionTimeData().containsKey(origClOrdId), "Order Last Execution Time data - OrigClOrdId has to be removed with Replaced Order");

        Method reflectedMethod1 = fixApplicationTest.getClass().getDeclaredMethod("resolveClubbingSettings",Message.class);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod1.invoke(fixApplicationTest , executionReport);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(exchangeExecutionMergerTest.getClOrdIdList().contains(clOrdId), "ClOrdId has to be added to Exchange Execution Merger ClOrdIdList");
    }

    @Test
    void checkOrderStatusCanceled_passTest() throws Exception{
        ExecutionReport executionReport = new ExecutionReport();
        String clOrdId = "1";
        String origClOrdId = "2";
        executionReport.setString(ClOrdID.FIELD, clOrdId);
        executionReport.setChar(OrdStatus.FIELD, OrdStatus.CANCELED);
        executionReport.setString(OrigClOrdID.FIELD, origClOrdId);
        fixApplicationTest.getOrderLastExecutionTimeData().put(origClOrdId, System.currentTimeMillis());

        Method reflectedMethod = fixApplicationTest.getClass().getDeclaredMethod("checkOrderStatus",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        boolean isAddToClub = (boolean) reflectedMethod.invoke(fixApplicationTest , executionReport);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(isAddToClub, "Canceled Order has to be added to the Clubbing.");
        Assertions.assertTrue(fixApplicationTest.getOrderLastExecutionTimeData().containsKey(clOrdId), "Order Last Execution Time data - ClOrdId has to be populated with Replaced Order");
        Assertions.assertFalse(fixApplicationTest.getOrderLastExecutionTimeData().containsKey(origClOrdId), "Order Last Execution Time data - OrigClOrdId has to be removed with Replaced Order");

        Method reflectedMethod1 = fixApplicationTest.getClass().getDeclaredMethod("resolveClubbingSettings",Message.class);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod1.invoke(fixApplicationTest , executionReport);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(exchangeExecutionMergerTest.getClOrdIdList().contains(clOrdId), "ClOrdId has to be added to Exchange Execution Merger ClOrdIdList");
    }

    @Test
    void checkExecutionTimeDiff_passTest() throws Exception{
        String clOrdId = "1";
        ExecutionReport executionReport = new ExecutionReport();
        long now = System.currentTimeMillis();
        Awaitility.await().atLeast(5, TimeUnit.MILLISECONDS);
        executionReport.setString(ClOrdID.FIELD, clOrdId);
        executionReport.setChar(OrdStatus.FIELD, OrdStatus.PARTIALLY_FILLED);
        settingsMockedStatic.when(()->Settings.getClubbingTimeOut()).thenReturn(10000L);
        fixApplicationTest.getOrderLastExecutionTimeData().put(clOrdId, now);

        Method reflectedMethod = fixApplicationTest.getClass().getDeclaredMethod("resolveClubbingSettings",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod.invoke(fixApplicationTest , executionReport);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertNotEquals(now, fixApplicationTest.getOrderLastExecutionTimeData().get(clOrdId).longValue(), "Partially Filled Order has to be added to the Clubbing.");
    }

    @Test
    void checkFilledQuantity_passTest() throws Exception{
        String clOrdId = "1";
        ExecutionReport executionReport = new ExecutionReport();
        long now = System.currentTimeMillis();
        executionReport.setString(ClOrdID.FIELD, clOrdId);
        executionReport.setChar(OrdStatus.FIELD, OrdStatus.PARTIALLY_FILLED);
        executionReport.setInt(OrderQty.FIELD, 10000);
        executionReport.setInt(LastShares.FIELD, 10);
        settingsMockedStatic.when(Settings::getClubbingRatio).thenReturn(0.005);
        fixApplicationTest.getOrderLastExecutionTimeData().put(clOrdId, now);

        Method reflectedMethod = fixApplicationTest.getClass().getDeclaredMethod("checkFilledQuantity",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        boolean isAddToClub = (boolean) reflectedMethod.invoke(fixApplicationTest , executionReport);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(isAddToClub, "Filled Order has to be added to the Clubbing.");

        Method reflectedMethod1 = fixApplicationTest.getClass().getDeclaredMethod("resolveClubbingSettings",Message.class);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod1.invoke(fixApplicationTest , executionReport);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(exchangeExecutionMergerTest.getClOrdIdList().contains(clOrdId), "ClOrdId has to be added to Exchange Execution Merger ClOrdIdList");
    }

    @Test
    void storeIntermediateQueueData_FlowTest() throws Exception{
        String clOrdId = "100";
        String account = "1";
        int mubasherNo = 1;
        Message message = new NewOrderSingle();
        message.setString(Account.FIELD, account);
        message.setString(ClOrdID.FIELD, clOrdId);
        message.setInt(FixConstants.FIX_TAG_10008, mubasherNo);

        Method reflectedMethod = fixApplicationTest.getClass().getDeclaredMethod("storeIntermediateQueueData",Message.class,boolean.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod.invoke(fixApplicationTest,message,IConstants.CONSTANT_TRUE);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertTrue(fixApplicationTest.getIntermediateQueueData().containsKey(account), "Trading Account has to be added to Intermediate Queue Data");
        Assertions.assertEquals(mubasherNo, fixApplicationTest.getIntermediateQueueData().get(account).intValue(), "MubasherNo has to be stored");
        Assertions.assertFalse(message.isSetField(FixConstants.FIX_TAG_10008), "Unwanted data used by DFIXRouter removed from Order request.");
        storeHAServerData_FlowTest(message, mubasherNo);//Sequential processing important.
    }

    void storeHAServerData_FlowTest(Message message, int mubasherNo) throws Exception{
        int serverId = 1;
        message.setInt(FixConstants.FIX_TAG_SERVED_BY, serverId);

        Method reflectedMethod = fixApplicationTest.getClass().getDeclaredMethod("storeHAServerData",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod.invoke(fixApplicationTest , message);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);

        Assertions.assertTrue(fixApplicationTest.getHAServerData().containsKey(mubasherNo), "Mubasher No has to be added to High Availability Data");
        Assertions.assertEquals(serverId, fixApplicationTest.getHAServerData().get(mubasherNo).intValue(), "Server Id has to be stored");
        Assertions.assertFalse(message.isSetField(FixConstants.FIX_TAG_SERVED_BY), "Unwanted data used by DFIXRouter removed from Order request.");
        populateIntermediateQueueData_FlowTest(message, mubasherNo, serverId);
    }
    void populateIntermediateQueueData_FlowTest(Message message, int mubasherNo, int serverId) throws Exception{
        DFIXMessage dfixMessage = new DFIXMessage();
        fixApplicationTest.populateIntermediateQueueData(message, dfixMessage);
        Assertions.assertEquals(mubasherNo, dfixMessage.getFixTag10008(), "MubasherNo has to be populated.");
        populateHAData_FlowTest(mubasherNo, serverId);//Sequential processing important.
    }

    void populateHAData_FlowTest(int mubasherNo, int serverId) throws Exception{
        settingsMockedStatic.when(()->Settings.getHostIdForOmsId(serverId)).thenReturn(serverId);
        DFIXMessage dfixMessage = new DFIXMessage();
        Message message = new Message();
        dfixMessage.setFixTag10008(mubasherNo);
        fixApplicationTest.populateHAData(message, dfixMessage);
        Assertions.assertEquals(serverId, dfixMessage.getServedBy(), "Server Id has to be populated.");
    }

    @Test
    void populateOMSReqIdMapTest() {
        String clOrdId = "200225000000056";
        String uniReqId = "1952_12_1582697766568";
        Message message = new Message();
        message.setString(ClOrdID.FIELD, clOrdId);
        message.setString(FixConstants.FIX_TAG_OMS_REQ_ID, uniReqId);

        fixApplicationTest.populateOMSReqIdMap(message);
        Assertions.assertEquals(uniReqId, fixApplicationTest.getClOrdIdOmsReqIdMap().get(clOrdId), "Unique Request Id is not matched");
        Assertions.assertFalse(message.isSetField(FixConstants.FIX_TAG_OMS_REQ_ID), "FIX_TAG_OMS_REQ_ID(10013) should be removed");
    }

    @Test
    void setOMSReqIdTest() throws Exception {
        String clOrdId = "200225000000066";
        String uniReqId = "1953_12_1582697766568";
        Message message = new Message();
        message.setString(ClOrdID.FIELD, clOrdId);
        fixApplicationTest.getClOrdIdOmsReqIdMap().put(clOrdId, uniReqId);

        fixApplicationTest.setOMSReqId(message);
        Assertions.assertEquals(uniReqId, message.getString(FixConstants.FIX_TAG_OMS_REQ_ID), "FIX_TAG_OMS_REQ_ID(10013) should be added");
    }

    @Test
    void fromApp_addCustomFieldsToOMS_SettingNotConfiguredTest() throws Exception {
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new NewOrderSingle();
        message.setString(ClOrdID.FIELD, "200225000000076");
        int initialLength = message.bodyLength();
        Properties prop = new Properties();
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);

        fixApplicationTest.addCustomFieldsToOMS(sessionSettingsTest, message, sessionId);
        Assertions.assertEquals(initialLength, message.bodyLength());
    }

    @Test
    void fromApp_addCustomFieldsToOMS_OneParaConfiguredTest() throws Exception {
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new NewOrderSingle();
        Properties prop = new Properties();
        prop.put(IConstants.SETTING_USER_DEFINED_TO_OMS_TAGS, "7999=DEFAULT_TENANT");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);

        fixApplicationTest.addCustomFieldsToOMS(sessionSettingsTest, message, sessionId);
        Assertions.assertEquals("DEFAULT_TENANT", message.getString(7999));
    }

    @Test
    void fromApp_addCustomFieldsToOMS_TwoParaConfiguredTest() throws Exception {
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new NewOrderSingle();
        Properties prop = new Properties();
        prop.put(IConstants.SETTING_USER_DEFINED_TO_OMS_TAGS, "7999=ABIC|8000=FIX");
        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId,true);

        fixApplicationTest.addCustomFieldsToOMS(sessionSettingsTest, message, sessionId);
        Assertions.assertEquals("ABIC", message.getString(7999));
        Assertions.assertEquals("FIX", message.getString(8000));
    }

    @Test
    void fromAdmin_LogOutMessage_ResetSeq_Flow_Test() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Message message = new Logout();
        message.setString(Text.FIELD,"Expected/Received =1/10");
        Mockito.doReturn("TDWL").when(fixClientTest).getSessionProperty(sessionId, IConstants.SESSION_IDENTIFIER);
        Mockito.doReturn(new Properties()).when(sessionSettingsTest).getSessionProperties(sessionId, true);

        Properties prop = new Properties();
        prop.put(IConstants.SETTING_TAG_INSTANT_AUTO_SYNC_SEQ_NO,IConstants.SETTING_YES);

        Mockito.doReturn(prop).when(sessionSettingsTest).getSessionProperties(sessionId, true);

        try (MockedStatic<AdminManager> adminManagerMockedStatic = Mockito.mockStatic(AdminManager.class)) {
            AdminManager adminManagerSpy = mock(AdminManager.class);
            adminManagerMockedStatic.when(() -> AdminManager.getInstance()).thenReturn(adminManagerSpy);
            Mockito.when(adminManagerSpy.resetOutSequence(anyString(), anyInt())).thenReturn("");
            fixApplicationTest.fromAdmin(message, sessionId);
            verify(adminManagerSpy, Mockito.times(1)).resetOutSequence(anyString(), anyInt());
        }
    }

    @Test
    void storeOpenOrdersWithHADetails() {
        Message message = new Message();

        //message 35!=DI tag expected to return true
        message.getHeader().setString(MsgType.FIELD, NewOrderSingle.MSGTYPE);
        Assertions.assertTrue(fixApplicationTest.storeOpenOrdersWithHADetails(message));

        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_DFIX_INFORMATION);
        message.setInt(ListID.FIELD, FixConstants.FIX_TAG_HA_DETAIL);
        message.setString(FixConstants.FIX_TAG_10008,"1");
        message.setString(Account.FIELD,IConstants.STRING_1);
        message.setString(ClOrdID.FIELD,IConstants.STRING_1);
        message.setString(FixConstants.FIX_TAG_SERVED_BY,IConstants.STRING_1);
        message.setString(FixConstants.FIX_TAG_10014,IConstants.STRING_1);

        ////message 35=DI tag expected to return false
        Assertions.assertFalse(fixApplicationTest.storeOpenOrdersWithHADetails(message));
    }

    @Test
    void clearCache_Test(){
        //Arrange
        fixApplicationTest.getAccountFixTag10008().put("Test",IConstants.CONSTANT_ONE_1);
        fixApplicationTest.getFixTag10008ServedBy().put(IConstants.CONSTANT_ONE_1,IConstants.CONSTANT_ONE_1);
        fixApplicationTest.getSimOrderProcess().add("Test");
        fixApplicationTest.getNonDisclosedTrdAccntSet().add("Test");

        //Act
        fixApplicationTest.clearCache();

        //Assert
        Assertions.assertTrue(fixApplicationTest.getAccountFixTag10008().isEmpty());
        Assertions.assertTrue(fixApplicationTest.getFixTag10008ServedBy().isEmpty());
        Assertions.assertTrue(fixApplicationTest.getSimOrderProcess().isEmpty());
        Assertions.assertTrue(fixApplicationTest.getNonDisclosedTrdAccntSet().isEmpty());
    }

    @Test
    void removeFromCache_Test() throws Exception {
        // Arrange
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.SIM_PROCESS_ICM_ORDERS)).thenReturn(IConstants.SETTING_YES);
        String key = "112131";

        FIXApplication fixApplication = new FIXApplication();

        fixApplication.getClOrdIdOmsReqIdMap().put(key,"value");
        fixApplication.getClOrdIdFixTag10008().put(key,1);
        fixApplication.getIcmOrdIdQueueIdMap().put(key,1);
        fixApplication.getIcmRemoteOrdIdQueueIdMap().put(key,1);
        Message message =  new Message();
        message.setString(ClOrdID.FIELD,"112131");

        //Act
        TestUtils.invokePrivateMethod(fixApplication,"removeFromCache",new Class<?>[]{String.class,char.class},key,'1');

        // Assert
        Assertions.assertFalse(fixApplication.getClOrdIdOmsReqIdMap().containsKey(key));
        Assertions.assertFalse(fixApplication.getClOrdIdFixTag10008().containsKey(key));
        Assertions.assertFalse(fixApplication.getIcmOrdIdQueueIdMap().containsKey(key));
        Assertions.assertFalse(fixApplication.getIcmRemoteOrdIdQueueIdMap().containsKey(key));
    }

    @ParameterizedTest
    @ValueSource(chars = {OrdStatus.FILLED,OrdStatus.REJECTED,OrdStatus.EXPIRED,OrdStatus.REPLACED,OrdStatus.CANCELED})
    void clearUnwantedData_Test(char ordStatus) throws Exception {
        // Arrange
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.SIM_PROCESS_ICM_ORDERS)).thenReturn(IConstants.SETTING_YES);

        FIXApplication fixApplication = new FIXApplication();
        String cliOrdid = "112131";
        String OrigCliOrdiD = "1121311";
        Message message =  new Message();

        //populate cliOrdId related map evtreis
        message.setString(ClOrdID.FIELD, cliOrdid);
        fixApplication.getClOrdIdOmsReqIdMap().put(cliOrdid,"value");
        fixApplication.getClOrdIdFixTag10008().put(cliOrdid,1);
        fixApplication.getIcmOrdIdQueueIdMap().put(cliOrdid,1);
        fixApplication.getIcmRemoteOrdIdQueueIdMap().put(cliOrdid,1);

        //populate OrigCliOrdId related map evtreis
        message.setChar(OrdStatus.FIELD,ordStatus);
        if (ordStatus == OrdStatus.CANCELED || ordStatus == OrdStatus.REPLACED) {
            message.setString(OrigClOrdID.FIELD, OrigCliOrdiD);
            fixApplication.getClOrdIdOmsReqIdMap().put(OrigCliOrdiD,"value1");
            fixApplication.getClOrdIdFixTag10008().put(OrigCliOrdiD,2);
            fixApplication.getIcmOrdIdQueueIdMap().put(OrigCliOrdiD,2);
            fixApplication.getIcmRemoteOrdIdQueueIdMap().put(OrigCliOrdiD,2);
        }

        //Act & Assert
        TestUtils.invokePrivateMethod(fixApplication,"clearUnwantedData",new Class<?>[]{Message.class},message);

        // Assert OrigCliOrdId is cleared from the Maps
        Assertions.assertFalse(fixApplication.getClOrdIdOmsReqIdMap().containsKey(OrigCliOrdiD));
        Assertions.assertFalse(fixApplication.getClOrdIdFixTag10008().containsKey(OrigCliOrdiD));
        Assertions.assertFalse(fixApplication.getIcmOrdIdQueueIdMap().containsKey(OrigCliOrdiD));
        Assertions.assertFalse(fixApplication.getIcmRemoteOrdIdQueueIdMap().containsKey(OrigCliOrdiD));

        if (ordStatus == OrdStatus.REPLACED) {
            // Assert cliOrdId is not cleared from the Maps for Repalced orders
            Assertions.assertTrue(fixApplication.getClOrdIdOmsReqIdMap().containsKey(cliOrdid));
            Assertions.assertTrue(fixApplication.getClOrdIdFixTag10008().containsKey(cliOrdid));
            Assertions.assertTrue(fixApplication.getIcmOrdIdQueueIdMap().containsKey(cliOrdid));
            Assertions.assertTrue(fixApplication.getIcmRemoteOrdIdQueueIdMap().containsKey(cliOrdid));
        } else {
            // Assert cliOrdId is cleared from the Maps
            Assertions.assertFalse(fixApplication.getClOrdIdOmsReqIdMap().containsKey(cliOrdid));
            Assertions.assertFalse(fixApplication.getClOrdIdFixTag10008().containsKey(cliOrdid));
            Assertions.assertFalse(fixApplication.getIcmOrdIdQueueIdMap().containsKey(cliOrdid));
            Assertions.assertFalse(fixApplication.getIcmRemoteOrdIdQueueIdMap().containsKey(cliOrdid));
        }

    }

    @Test
    void populateSessionNoData_Test() {
        //Arrange
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        SessionID sessionId1 = new SessionID("FIX.4.2","SLGM1","TDWL");

        fixApplicationTest.getSessionNoData().clear();

        //Act
        fixApplicationTest.populateSessionNoData(sessionId);

        //Assert
        Assertions.assertTrue(fixApplicationTest.getSessionNoData().size()==1);
        Assertions.assertTrue(fixApplicationTest.getSessionNoData().containsKey(sessionId.toString()));

        //Act
        fixApplicationTest.populateSessionNoData(sessionId1);

        //Assert
        Assertions.assertTrue(fixApplicationTest.getSessionNoData().size()==2);
        Assertions.assertTrue(fixApplicationTest.getSessionNoData().containsKey(sessionId1.toString()));

    }

    @ParameterizedTest
    @ValueSource(booleans = {IConstants.CONSTANT_TRUE, IConstants.CONSTANT_FALSE})
    void sendLinkStatusConnected_Test(boolean watchdogEnabled) {
        //Arrange
        when(fixApplicationTest.isWatchdogEnabled()).thenReturn(watchdogEnabled);
        SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
        Mockito.doReturn("TDWL").when(fixApplicationTest).getSessionIdentifier(sessionId);

        try (MockedStatic<WatchDogHandler> watchDogHandlerMockedStatic = mockStatic(WatchDogHandler.class)) {
            //Act
            fixApplicationTest.sendLinkStatusConnected(IConstants.STRING_1, sessionId);

            //Assert
            watchDogHandlerMockedStatic.verify(() -> WatchDogHandler.sendExchangeLinkStatus(anyString(), anyString(), any(com.dfn.watchdog.commons.State.class), anyString()), Mockito.times(watchdogEnabled ? IConstants.CONSTANT_ONE_1 : IConstants.CONSTANT_ZERO_0));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {IConstants.CONSTANT_TRUE, IConstants.CONSTANT_FALSE})
    void sendLinkStatusDisConnected_Test(boolean watchdogEnabled){
        //Arrange
        when(fixApplicationTest.isWatchdogEnabled()).thenReturn(watchdogEnabled);
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Mockito.doReturn("TDWL").when(fixApplicationTest).getSessionIdentifier(sessionId);

        try (MockedStatic<WatchDogHandler> watchDogHandlerMockedStatic = mockStatic(WatchDogHandler.class)) {
            //Act
            fixApplicationTest.sendLinkStatusDisconnected(IConstants.STRING_1, sessionId);

            //Assert
            watchDogHandlerMockedStatic.verify(() -> WatchDogHandler.sendExchangeLinkStatus(anyString(), anyString(), any(State.class), anyString()), Mockito.times(watchdogEnabled ? IConstants.CONSTANT_ONE_1 : IConstants.CONSTANT_ZERO_0));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {MsgType.ORDER_SINGLE, MsgType.ORDER_CANCEL_REPLACE_REQUEST,MsgType.ORDER_CANCEL_REQUEST})
    void populateCliOrdIdForRejectionMsg_Test1(String msgType) throws Exception {
        //Arrange
        Message rejMsg = new Message();
        Message requestMsg = new Message();
        requestMsg.getHeader().setString(MsgType.FIELD,msgType);
        requestMsg.setString(ClOrdID.FIELD,"123456");

        //Act
        TestUtils.invokePrivateMethod(fixApplicationTest,"populateCliOrdIdForRejectionMsg",new Class[]{Message.class,Message.class},rejMsg,requestMsg);

        //Assert
        Assertions.assertEquals("123456",rejMsg.getString(ClOrdID.FIELD));
    }

    @Test
    void populateCliOrdIdForRejectionMsg_Test2() throws Exception {
        //Arrange
        Message rejMsg = new Message();
        Message requestMsg = new Message();
        requestMsg.getHeader().setString(MsgType.FIELD,MsgType.EXECUTION_REPORT);
        requestMsg.setString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID,"123456");

        //Act
        TestUtils.invokePrivateMethod(fixApplicationTest,"populateCliOrdIdForRejectionMsg",new Class[]{Message.class,Message.class},rejMsg,requestMsg);

        //Assert
        Assertions.assertEquals("123456",rejMsg.getString(ClOrdID.FIELD));
    }


    @Test
    void simProcessICMOrdToPopulateIntermediateQueueData_Test() throws Exception {
        when(fixApplicationTest.isSimProcessICMOrders()).thenReturn(IConstants.CONSTANT_TRUE);
        final String cliOrdId = "123456";
        final String OriCliOrdId = "123455";
        final int icmQueueId = IConstants.CONSTANT_ONE_1;


        //icmOrdIdQueueIdMap contains CliOrdId
        fixApplicationTest.getIcmOrdIdQueueIdMap().put(cliOrdId,icmQueueId);

        Message message = new Message();
        message.setString(ClOrdID.FIELD, cliOrdId);
        DFIXMessage dfixMessage = new DFIXMessage();


        //Act
        TestUtils.invokePrivateMethod(fixApplicationTest,"simProcessICMOrdToPopulateIntermediateQueueData",new Class[]{Message.class,DFIXMessage.class},message,dfixMessage);

        //Assert
        Assertions.assertTrue(dfixMessage.isSimProcessing());

        //icmOrdIdQueueIdMap does not contains CliOrdId but contains OrigCliOrdId
        fixApplicationTest.getIcmOrdIdQueueIdMap().clear();
        message.setString(OrigClOrdID.FIELD,OriCliOrdId);


        //icmRemoteOrdIdQueueIdMap is empty but icmOrdIdQueueIdMap has OrigCliOrdId record => IntermediateQueueId loaded from icmOrdIdQueueIdMap
        fixApplicationTest.getIcmOrdIdQueueIdMap().put(OriCliOrdId,icmQueueId);
        TestUtils.invokePrivateMethod(fixApplicationTest,"simProcessICMOrdToPopulateIntermediateQueueData",new Class[]{Message.class,DFIXMessage.class},message,dfixMessage);
        Assertions.assertTrue(dfixMessage.isSimProcessing());

        //icmRemoteOrdIdQueueIdMap has OrigCliOrdId record => IntermediateQueueId loaded from icmRemoteOrdIdQueueIdMap
        fixApplicationTest.getIcmRemoteOrdIdQueueIdMap().put(OriCliOrdId,icmQueueId);
        TestUtils.invokePrivateMethod(fixApplicationTest,"simProcessICMOrdToPopulateIntermediateQueueData",new Class[]{Message.class,DFIXMessage.class},message,dfixMessage);
        Assertions.assertTrue(dfixMessage.isSimProcessing());
    }

    @Test
    void getAccToPopulateInterQueueData_Tes() throws Exception {
        Message message = new Message();
        message.setString(Account.FIELD,"123456");
        final String acc = (String) TestUtils.invokePrivateMethod(fixApplicationTest, "getAccToPopulateInterQueueData", new Class[]{Message.class}, message);
        Assertions.assertEquals("123456",acc);

        //populate partyGroup
        Group partyGroup = new Group(NoPartyIDs.FIELD, PartyID.FIELD,
                new int[]{PartyID.FIELD, PartyIDSource.FIELD, PartyRole.FIELD});
        // Set the field values for the group
        partyGroup.setField(new PartyID("123455"));
        partyGroup.setField(new PartyIDSource(PartyIDSource.PROPRIETARY_CUSTOM_CODE));
        partyGroup.setField(new PartyRole(PartyRole.CLIENT_ID));
        message.addGroup(partyGroup);
        final String acc1 = (String) TestUtils.invokePrivateMethod(fixApplicationTest, "getAccToPopulateInterQueueData", new Class[]{Message.class}, message);
        Assertions.assertEquals("123455",acc1);
    }

    @ParameterizedTest
    @ValueSource(strings = {NewOrderSingle.MSGTYPE,OrderCancelReplaceRequest.MSGTYPE})
    void checkFlowControl_Test(String msgType) throws Exception {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, msgType);
        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Mockito.doReturn("TDWL").when(fixApplicationTest).getSessionIdentifier(sessionId);
        fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClientTest);
        FlowController flowController = mock(FlowController.class);
        fixClientTest.getFlowControllers().put(sessionId, flowController);
        ScheduledExecutorService schExService = mock(ScheduledExecutorService.class);
        when(fixClientTest.getFlowControlPool()).thenReturn(schExService);
        when(flowController.isSendMessage(msg,IConstants.CONSTANT_TRUE)).thenReturn(FlowControlStatus.ALLOWED);

        final boolean isMessageAllowed = (boolean) TestUtils.invokePrivateMethod(fixApplicationTest, "checkFlowControl", new Class[]{Message.class, SessionID.class, boolean.class}, msg, sessionId, IConstants.CONSTANT_TRUE);

        Assertions.assertTrue(isMessageAllowed);
    }

    @Test
    void flowControlForwardRejectMessage_Test() throws Exception {
        Message message = new Message();
        message.getHeader().setString(MsgType.FIELD, OrderCancelRequest.MSGTYPE);

        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Mockito.doReturn(IConstants.SETTING_YES).when(fixClientTest).getSessionProperty(sessionId, IConstants.SETTING_TAG_IS_REJECT_MESSAGE);
        Mockito.doNothing().when(fixApplicationTest).populateHAData(any(Message.class), any(DFIXMessage.class));
        Mockito.doNothing().when(fixApplicationTest).populateIntermediateQueueData(any(Message.class), any(DFIXMessage.class));

        message.setString(ClOrdID.FIELD,"123456");
        TestUtils.invokePrivateMethod(fixApplicationTest, "flowControlForwardRejectMessage", new Class[]{Message.class, SessionID.class, boolean.class, boolean.class, FlowControlStatus.class,String.class}, message, sessionId, IConstants.CONSTANT_FALSE,IConstants.CONSTANT_FALSE, FlowControlStatus.ALLOWED,"TDWL");
    }
}
