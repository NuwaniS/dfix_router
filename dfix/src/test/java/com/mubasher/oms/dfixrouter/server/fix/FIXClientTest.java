package com.mubasher.oms.dfixrouter.server.fix;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import quickfix.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by nuwanis on 8/31/2017.
 */
class FIXClientTest{

    @Spy
    FIXClient fixClient;
    @Spy
    SessionSettings sessionSettings;


    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        FIXClient.setSettings(sessionSettings);
    }


    @AfterAll
    public static void teardown() throws IOException {
        TestUtils.deleteDirectory("./quick_fix/");
        TestUtils.deleteDirectory("./logs/");
    }


    @Test
    void stopFIXClientTest() {
        doNothing().when(fixClient).logoutFromFIXGateway();
        fixClient.stopFIXClient();
        verify(fixClient, times(1)).logoutFromFIXGateway();
    }

    @Test
    void runEOD_AllSessionTest() throws Exception {
        SessionID sessionId1 = new SessionID("FIX.4.2","SLGM0","TDWL");
        SessionID sessionId2 = new SessionID("FIX.4.2","SLGM0","NSDQ");
        ArrayList<SessionID> initiatorSessionList =new ArrayList<>();
        Collections.addAll(initiatorSessionList,sessionId1 ,sessionId2);
        doReturn(initiatorSessionList).when(fixClient).getInitiatorSessionList();
        doReturn("TDWL").when(fixClient).getSessionProperty(sessionId1, IConstants.SESSION_IDENTIFIER);
        doReturn("NSDQ").when(fixClient).getSessionProperty(sessionId2, IConstants.SESSION_IDENTIFIER);
        doNothing().when(fixClient).setOutGoingSeqNo("TDWL",1);
        doNothing().when(fixClient).setOutGoingSeqNo("NSDQ",1);
        doNothing().when(fixClient).setInComingSeqNo("TDWL",1);
        doNothing().when(fixClient).setInComingSeqNo("NSDQ",1);
        fixClient.runEOD("ALL");
        verify(fixClient, times(1)).setOutGoingSeqNo("TDWL",1);
        verify(fixClient, times(1)).setOutGoingSeqNo("NSDQ",1);
        verify(fixClient, times(1)).setInComingSeqNo("TDWL",1);
        verify(fixClient, times(1)).setInComingSeqNo("NSDQ",1);
    }

    @Test
    void runEOD_TDWLSessionTest() throws Exception {
        ArrayList<SessionID> initiatorSessionList =new ArrayList<>();
        doReturn(initiatorSessionList).when(fixClient).getInitiatorSessionList();
        doNothing().when(fixClient).setOutGoingSeqNo("TDWL",1);
        doNothing().when(fixClient).setInComingSeqNo("TDWL",1);
        fixClient.runEOD("TDWL");
        verify(fixClient, times(1)).setOutGoingSeqNo("TDWL",1);
        verify(fixClient, times(1)).setInComingSeqNo("TDWL",1);
    }

    @Test
    void getExpectedTargetSeqNumber_NullSessionTest() {
        SessionID sessionId = new SessionID("FIX.4.2", "TDWL", "SLGM0");
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(null);
            try {
                if (fixClient.getExpectedTargetSeqNumber(sessionId) > 0) {
                    Assertions.fail("Expected a Null value.");
                }
            } catch (NullPointerException ignored) {
            }
        }
    }

    @Test
    void getExpectedTargetSeqNumber_SpecificSessionTest1()  {
        SessionID sessionId = new SessionID("FIX.4.2","TDWL","SLGM0");
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(mockedSession);
            Assertions.assertEquals(0, fixClient.getExpectedTargetSeqNumber(sessionId));
        }
    }

    @Test
    void getExpectedTargetSeqNumber_SpecificSessionTest2()  {
        SessionID sessionId = new SessionID("FIX.4.2","NSDQ","SLGM1");
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(()->Session.lookupSession(sessionId)).thenReturn(mockedSession);
            Assertions.assertEquals(0, fixClient.getExpectedTargetSeqNumber(sessionId));
        }
    }

    @Test
    void getExpectedSenderSeqNumber_NullSessionTest() {
        SessionID sessionId = new SessionID("FIX.4.2", "TDWL", "SLGM0");
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(null);
            try {
                if (fixClient.getExpectedSenderSeqNumber(sessionId) > 0) {
                    Assertions.fail("Expected a Null value.");
                }
            } catch (NullPointerException ignored) {

            }
        }
    }

    @Test
    void getExpectedSenderSeqNumber_SpecificSessionTest1() {
        SessionID sessionId = new SessionID("FIX.4.2","TDWL","SLGM0");
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(mockedSession);
            Assertions.assertEquals(0, fixClient.getExpectedSenderSeqNumber(sessionId));
        }
    }

    @Test
    void getExpectedSenderSeqNumber_SpecificSessionTest2() {
        SessionID sessionId = new SessionID("FIX.4.2","TDWL","SLGM1");
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(()->Session.lookupSession(sessionId)).thenReturn(mockedSession);
            Assertions.assertEquals(0, fixClient.getExpectedSenderSeqNumber(sessionId));
        }
    }

    @Test
    void getSessionStatus_SessionLoggedOnTest() {
        SessionID sessionId = new SessionID("FIX.4.2","TDWL","SLGM1");
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(mockedSession);
            doReturn(true).when(mockedSession).isLoggedOn();
            Assertions.assertEquals("CONNECTED", fixClient.getSessionStatus(sessionId));
        }
    }

    @Test
    void getSessionStatus_SessionLoggedOutTest() {
        SessionID sessionId = new SessionID("FIX.4.2","TDWL","SLGM1");
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(mockedSession);
            doReturn(false).when(mockedSession).isLoggedOn();
            Assertions.assertEquals("DISCONNECTED", fixClient.getSessionStatus(sessionId));
        }
    }

    @Test
    void getSessionStatus_SessionNullTest() {
        SessionID sessionId = new SessionID("FIX.4.2","TDWL","SLGM1");
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(null);
            Assertions.assertEquals("DOWN", fixClient.getSessionStatus(sessionId));
        }
    }

    @Test
    void getSessionStatus_SessionIDNullTest() {
        Assertions.assertEquals("DOWN", fixClient.getSessionStatus(null));
    }

    @Test
    void setOutGoingSeqNoTest() throws Exception{
        SessionID sessionId = new SessionID("FIX.4.2","TDWL","SLGM1");
        doReturn(sessionId).when(fixClient).getSessionID("TDWL");
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(mockedSession);
            fixClient.setOutGoingSeqNo("TDWL", 1);
            verify(mockedSession, times(1)).setNextSenderMsgSeqNum(1);
        }

    }

   @Test
    void setOutGoingSeqNo_SessionIdentifierNullTest() throws Exception{
       Assertions.assertThrows(NullPointerException.class,()->fixClient.setOutGoingSeqNo(null,1));
    }

    @Test
    void setInComingSeqNo() throws Exception{

        SessionID sessionId = new SessionID("FIX.4.2","TDWL","SLGM1");
        doReturn(sessionId).when(fixClient).getSessionID("TDWL");
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(mockedSession);
            fixClient.setInComingSeqNo("TDWL", 1);
            verify(mockedSession, times(1)).setNextTargetMsgSeqNum(1);
        }

    }

    @Test
    void setInComingSeqNo_SessionIdentifierNullTest() throws Exception{
        Assertions.assertThrows(NullPointerException.class,()->        fixClient.setInComingSeqNo(null,1));
    }

    @Test
    void disconnectInitiatorSession_AllSessionsNotEmptySessionListTest() throws Exception {
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class);
            MockedStatic<FIXClient> fixClientMockedStatic = mockStatic(FIXClient.class);) {
            SessionID sessionId1 = new SessionID("FIX.4.2", "SLGM0", "TDWL");
            SessionID sessionId2 = new SessionID("FIX.4.2", "SLGM0", "NSDQ");
            Properties prop = new Properties();
            prop.put(IConstants.SETTING_CONNECTION_TYPE, IConstants.INITIATOR_CONNECTION_TYPE);
            prop.put(IConstants.SETTING_RECONNECT_INTERVAL, 5);
            doReturn(prop).when(sessionSettings).getSessionProperties(sessionId1, true);
            doReturn(prop).when(sessionSettings).getSessionProperties(sessionId2, true);
            ArrayList<SessionID> initiatorSessionList = new ArrayList<>();
            Collections.addAll(initiatorSessionList, sessionId1, sessionId2);
            doReturn(initiatorSessionList).when(fixClient).getInitiatorSessionList();
            HashMap<SessionID, SessionSettings> settings = new HashMap<>();
            settings.put(sessionId1, sessionSettings);
            settings.put(sessionId2, sessionSettings);
            fixClientMockedStatic.when(FIXClient::getSettings).thenReturn(settings);
            Session mockedSession = mock(Session.class);
            sessionMockedStatic.when(()->Session.lookupSession(sessionId1)).thenReturn(mockedSession);
            sessionMockedStatic.when(()->Session.lookupSession(sessionId2)).thenReturn(mockedSession);
            doNothing().when(mockedSession).logout("user requested");
            fixClient.disconnectSession("ALL");
            verify(mockedSession, times(2)).logout("user requested");
        }

    }

    @Test
    void disconnectInitiatorSession_AllSessionsEmptySessionListTest() {

        ArrayList<SessionID> initiatorSessionList = new ArrayList<>();
        doReturn(initiatorSessionList).when(fixClient).getInitiatorSessionList();
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> ignored = mockStatic(Session.class)) {
            fixClient.disconnectSession("ALL");
            verify(mockedSession, times(0)).logout("user requested");
        }
    }

    @Test
    void disconnectInitiatorSession_TDWLSessionTest() {

        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        ArrayList<SessionID> initiatorSessionList =new ArrayList<>();
        Collections.addAll(initiatorSessionList,sessionId);
        doReturn(initiatorSessionList).when(fixClient).getInitiatorSessionList();
        Session mockedSession = mock(Session.class);
        doReturn(sessionId).when(fixClient).getSessionID("TDWL");
        doNothing().when(mockedSession).logout("user requested");
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(mockedSession);
            fixClient.disconnectSession("TDWL");
            verify(mockedSession, times(1)).logout("user requested");
        }
    }

    @Test
    void disconnectInitiatorSession_TDWLSessionNotConfiguredTest() {
        SessionID sessionId1 = new SessionID("FIX.4.2","SLGM0","TDWL");
        SessionID sessionId2 = new SessionID("FIX.4.2","SLGM0","NSDQ");
        ArrayList<SessionID> initiatorSessionList = new ArrayList<>();
        Collections.addAll(initiatorSessionList,sessionId1);
        doReturn(initiatorSessionList).when(fixClient).getInitiatorSessionList();
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(()->Session.lookupSession(sessionId2)).thenReturn(mockedSession);
            doReturn(sessionId2).when(fixClient).getSessionID("TDWL");
            doNothing().when(mockedSession).logout("user requested");
            fixClient.disconnectSession("TDWL");
            Assertions.assertTrue(true);
        }
    }

    @Test
    void connectInitiatorSession_AllSessionsNotEmptySessionListTest() {

        SessionID sessionId1 = new SessionID("FIX.4.2","SLGM0","TDWL");
        SessionID sessionId2 = new SessionID("FIX.4.2","SLGM0","NSDQ");
        ArrayList<SessionID> initiatorSessionList =new ArrayList<>();
        Collections.addAll(initiatorSessionList,sessionId1 ,sessionId2);
        doReturn(initiatorSessionList).when(fixClient).getInitiatorSessionList();
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId1)).thenReturn(mockedSession);
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId2)).thenReturn(mockedSession);
            doNothing().when(mockedSession).logon();
            doReturn(true).when(mockedSession).isLoggedOn();
            fixClient.connectSession("ALL");
            Assertions.assertEquals("FIX.4.2:SLGM0->TDWL", sessionId1.toString());
            Assertions.assertEquals("FIX.4.2:SLGM0->NSDQ", sessionId2.toString());
            verify(mockedSession, times(2)).logon();
        }

    }

    @Test
    void connectInitiatorSession_AllSessionsEmptySessionListTest() {
        ArrayList<SessionID> initiatorSessionList = new ArrayList<>();
        doReturn(initiatorSessionList).when(fixClient).getInitiatorSessionList();
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            fixClient.connectSession("ALL");
            verify(mockedSession, times(0)).logon();
        }
    }

    @Test
    void connectInitiatorSession_TDWLSessionTest() throws ConfigError {

        SessionID sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class);
        MockedStatic<FIXClient> fixClientMockedStatic =  mockStatic(FIXClient.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId)).thenReturn(mockedSession);
            doReturn(sessionId).when(fixClient).getSessionID("TDWL");
            doNothing().when(mockedSession).logon();
            doReturn(true).when(mockedSession).isLoggedOn();
            Properties prop = new Properties();
            prop.put(IConstants.SETTING_CONNECTION_TYPE, IConstants.INITIATOR_CONNECTION_TYPE);
            prop.put(IConstants.SETTING_RECONNECT_INTERVAL, 5);
            doReturn(prop).when(sessionSettings).getSessionProperties(sessionId, true);
            HashMap<SessionID, SessionSettings> settings = new HashMap<>();
            settings.put(sessionId, sessionSettings);
            fixClientMockedStatic.when(FIXClient::getSettings).thenReturn(settings);
            fixClient.connectSession("TDWL");
            Assertions.assertEquals("FIX.4.2:SLGM0->TDWL", fixClient.getSessionID("TDWL").toString());
            verify(mockedSession, times(1)).logon();
        }

    }

    @Test
    void connectInitiatorSession_TDWLSessionNotConfiguredTest() {

        SessionID sessionId1 = new SessionID("FIX.4.2","SLGM0","TDWL");
        SessionID sessionId2 = new SessionID("FIX.4.2","SLGM0","NSDQ");
        ArrayList<SessionID> initiatorSessionList =new ArrayList<>();
        Collections.addAll(initiatorSessionList,sessionId1);
        doReturn(initiatorSessionList).when(fixClient).getInitiatorSessionList();
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class)) {
            sessionMockedStatic.when(() -> Session.lookupSession(sessionId2)).thenReturn(mockedSession);
            doReturn(sessionId2).when(fixClient).getSessionID("TDWL");
            doReturn(true).when(mockedSession).isLoggedOn();
            fixClient.connectSession("TDWL");
            Assertions.assertTrue(true);
        }

    }


    @Test
    void getSessionIdentifierTest() throws Exception{
        try (MockedStatic<FIXClient> fixClientMockedStatic = mockStatic(FIXClient.class)) {
            SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
            Properties prop = new Properties();
            prop.put("SessionIdentifier", "96=mubasher|101=1234");
            doReturn(prop).when(sessionSettings).getSessionProperties(sessionId, true);
            HashMap<SessionID, SessionSettings> settings = new HashMap<>();
            settings.put(sessionId, sessionSettings);
            fixClientMockedStatic.when(FIXClient::getSettings).thenReturn(settings);
            Assertions.assertEquals("96=mubasher|101=1234", fixClient.getSessionProperty(sessionId, IConstants.SESSION_IDENTIFIER));
        }
    }

    @Test
    void getSessionIdentifier_ExceptionTest() {
        Assertions.assertNull(fixClient.getSessionProperty(null, IConstants.SESSION_IDENTIFIER));
    }

    @Test
    void startFIXClientPassTest() throws ConfigError {
        ClassLoader classLoader = getClass().getClassLoader();
        String cfgFileName = "correctSessions_1.cfg";
        String path = classLoader.getResource(cfgFileName).getPath();
        final String dataDictionaryPath = getClass().getClassLoader().getResource("FIX42_TDWL.xml").getPath();
        try (MockedStatic<Settings> settingsMockedStatic = mockStatic(Settings.class);
             MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic = mockStatic(DFIXRouterManager.class)) {
            settingsMockedStatic.when(Settings::getCfgFileName).thenReturn(path);
            dfixRouterManagerMockedStatic.when(DFIXRouterManager::isFixClientEnable).thenReturn(IConstants.CONSTANT_TRUE);
            doReturn(new DataDictionary(dataDictionaryPath)).when(fixClient).getDataDictionary(any());
            fixClient.startFIXClient();
            Assertions.assertNotNull(fixClient.getFlowControlPool());
        }
    }

    @Test
    void reload_test() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        final String dataDictionaryPath = classLoader.getResource("FIX42_TDWL.xml").getPath();
        String cfgFileName = "correctSessions_1.cfg";
        String path = classLoader.getResource(cfgFileName).getPath();
        ThreadedSocketInitiator threadedSocketInitiatorMock = mock(ThreadedSocketInitiator.class);
        ThreadedSocketAcceptor threadedSocketAcceptorMock = mock(ThreadedSocketAcceptor.class);
        Session mockedSession = mock(Session.class);
        try (MockedStatic<Settings> settingsMockedStatic = mockStatic(Settings.class);
             MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic = mockStatic(DFIXRouterManager.class);
             MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class);
             ) {
            settingsMockedStatic.when(Settings::getCfgFileName).thenReturn(path);
            dfixRouterManagerMockedStatic.when(DFIXRouterManager::isFixClientEnable).thenReturn(IConstants.CONSTANT_TRUE);
            sessionMockedStatic.when(()->Session.lookupSession(any(SessionID.class))).thenReturn(mockedSession);
            doReturn(new DataDictionary(dataDictionaryPath)).when(fixClient).getDataDictionary(any());
            fixClient.startFIXClient();
            doReturn(threadedSocketInitiatorMock).when(fixClient).getConnector(any(SessionSettings.class),any(SessionID.class),eq(IConstants.INITIATOR_CONNECTION_TYPE));
            doReturn(threadedSocketAcceptorMock).when(fixClient).getConnector(any(SessionSettings.class),any(SessionID.class),eq(IConstants.ACCEPTOR_CONNECTION_TYPE));

            final List<String> reloadedSessionsList = (List<String>) TestUtils.invokePrivateMethod(fixClient, "reload", new Class[]{}, null);
            Assertions.assertTrue(reloadedSessionsList.contains("TDWL_CHAR_MORE_10"));
            Assertions.assertTrue(reloadedSessionsList.contains("NSDQ1"));
        }
    }
    @Test
    void getConnector_test() throws ConfigError {
        SessionSettings sessionSettings = mock(SessionSettings.class);
        SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");

        final Connector connector = fixClient.getConnector(sessionSettings, sessionId, IConstants.INITIATOR_CONNECTION_TYPE);
        Assertions.assertNotNull(connector);
        Assertions.assertTrue(connector instanceof ThreadedSocketInitiator);
        final Connector connector1 = fixClient.getConnector(sessionSettings, sessionId, IConstants.ACCEPTOR_CONNECTION_TYPE);
        Assertions.assertNotNull(connector1);
        Assertions.assertTrue(connector1 instanceof ThreadedSocketAcceptor);
    }

    @Test
    void startSimulator_stopFIXSimulator_test() throws ConfigError {
        ClassLoader classLoader = getClass().getClassLoader();
        String path = classLoader.getResource("sim.cfg").getPath();
        final String dataDictionaryPath = classLoader.getResource("FIX42_TDWL.xml").getPath();
        DataDictionary dataDictionary  =  fixClient.getDataDictionary(dataDictionaryPath);
        doReturn(dataDictionary).when(fixClient).getDataDictionary(any());

        try(MockedStatic<Settings> settingsMockedStatic = mockStatic(Settings.class)) {
            settingsMockedStatic.when(Settings::isSimulatorOn).thenReturn(IConstants.CONSTANT_TRUE);
            settingsMockedStatic.when(Settings::getSimCfgFileName).thenReturn(path);
            fixClient.startSimulator();
            Assertions.assertNotNull(fixClient.getSimAcceptor());
            Assertions.assertNotNull(fixClient.getSimInitiator());
            DFIXRouterManager.getInstance().setDfixRouterStarted(IConstants.CONSTANT_FALSE);
            }
    }

    @Test
    void getFileLogFactory_test() throws Exception {
        SessionID sessionId1 = new SessionID("FIX.4.2", "SLGM0_CHAR_MORE_15", "TDWL_CHAR_MORE_15","TDWL");
        SessionID sessionId2 = new SessionID("FIX.4.2", "SLGM11", "NSDQ1");
        String path = getClass().getClassLoader().getResource("correctSessions_1.cfg").getPath();


        try (MockedStatic<Settings> settingsMockedStatic = mockStatic(Settings.class)) {
            final SessionSettings sessionSettings1 = (SessionSettings) TestUtils.invokePrivateMethod(fixClient, "getSessionSettings", new Class[]{FileInputStream.class}, new FileInputStream(path));
            settingsMockedStatic.when(()->Settings.getInt(SettingsConstants.FIX_LOG_TYPE)).thenReturn(IConstants.SETTING_FIX_LOG_TYPE_SEPERATE_FILE);
            final LogFactory LogFactory = (LogFactory) TestUtils.invokePrivateMethod(fixClient, "getFileLogFactory", new Class[]{SessionSettings.class}, sessionSettings1);
            Assertions.assertNotNull(LogFactory);
            Assertions.assertEquals("TDWL_CHAR_MORE_10.event",sessionSettings1.getSessionProperties(sessionId1).get("SLF4JLogEventCategory"));
            Assertions.assertEquals("TDWL_CHAR_MORE_10.errorEvent",sessionSettings1.getSessionProperties(sessionId1).get("SLF4JLogErrorEventCategory"));
            Assertions.assertEquals("TDWL_CHAR_MORE_10.incoming",sessionSettings1.getSessionProperties(sessionId1).get("SLF4JLogIncomingMessageCategory"));
            Assertions.assertEquals("TDWL_CHAR_MORE_10.outgoing",sessionSettings1.getSessionProperties(sessionId1).get("SLF4JLogOutgoingMessageCategory"));
            Assertions.assertEquals("NSDQ1.event",sessionSettings1.getSessionProperties(sessionId2).get("SLF4JLogEventCategory"));
            Assertions.assertEquals("NSDQ1.errorEvent",sessionSettings1.getSessionProperties(sessionId2).get("SLF4JLogErrorEventCategory"));
            Assertions.assertEquals("NSDQ1.incoming",sessionSettings1.getSessionProperties(sessionId2).get("SLF4JLogIncomingMessageCategory"));
            Assertions.assertEquals("NSDQ1.outgoing",sessionSettings1.getSessionProperties(sessionId2).get("SLF4JLogOutgoingMessageCategory"));

        }
    }

    @Test
    void populateFlowControlData_Test() throws Exception {
        SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
        doReturn(IConstants.SETTING_YES).when(fixClient).getSessionProperty(sessionId, IConstants.SETTING_TAG_VALIDATE_MESSAGE_FLOW);
        doReturn("10").when(fixClient).getSessionProperty(sessionId, IConstants.SETTING_TAG_NEW_MESSAGE_RATE);
        doReturn("10").when(fixClient).getSessionProperty(sessionId, IConstants.SETTING_TAG_NEW_WINDOW_LIMIT);
        doReturn("10").when(fixClient).getSessionProperty(sessionId, IConstants.SETTING_TAG_AMEND_MESSAGE_RATE);
        doReturn("10").when(fixClient).getSessionProperty(sessionId, IConstants.SETTING_TAG_AMEND_WINDOW_LIMIT);
        doReturn("10").when(fixClient).getSessionProperty(sessionId, IConstants.SETTING_TAG_DUPLICATE_WINDOW_LIMIT);

        TestUtils.invokePrivateMethod(fixClient,"populateFlowControlData",new Class[]{SessionID.class},sessionId);

        Assertions.assertFalse(fixClient.getFlowControllers().isEmpty());
    }


    @Test
    void sendString_Test() throws InvalidMessage, SessionNotFound {
        //SessionDown
        SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
        Message msg = new Message();
        doReturn(sessionId).when(fixClient).getSessionID("TDWL");
        FIXApplication fixApplicationMock = mock(FIXApplication.class);
        doReturn(fixApplicationMock).when(fixClient).getApplication();
        doReturn(IConstants.CONSTANT_TRUE).when(fixApplicationMock).storeOpenOrdersWithHADetails(any(Message.class));
        fixClient.getPendingMessagesMap().put("TDWL",new com.objectspace.jgl.Queue());

        fixClient.sendString(msg.toString(),"TDWL");

        Assertions.assertFalse(fixClient.getPendingMessagesMap().get("TDWL").isEmpty());
    }
}
