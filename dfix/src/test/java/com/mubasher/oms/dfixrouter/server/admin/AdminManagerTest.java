package com.mubasher.oms.dfixrouter.server.admin;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.cluster.DFIXCluster;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import quickfix.DataDictionary;
import quickfix.SessionID;
import quickfix.SessionSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by isharaw on 9/25/2017.
 */
class AdminManagerTest {

    @Spy
    AdminManager adminManagerTest ;
    @Spy
    DFIXRouterManager dfixRouterManagerTest ;
    @Spy
    FIXClient fixClientTest ;
    @Spy
    DFIXCluster dfixCluster =  DFIXCluster.getInstance();

    MockedStatic<Settings> settingsMockedStatic;
    MockedStatic<FIXClient> fixClientMockedStatic;
    MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
        dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);

        settingsMockedStatic.when(() -> Settings.getInt(SettingsConstants.SETTING_DFIX_ID)).thenReturn(IConstants.CONSTANT_ONE_1);
        dfixRouterManagerMockedStatic.when(DFIXRouterManager::getInstance).thenReturn(dfixRouterManagerTest);
        fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClientTest);
    }

    @AfterEach
    public void tearDown() {
        settingsMockedStatic.close();
        fixClientMockedStatic.close();
        dfixRouterManagerMockedStatic.close();
    }

    @Test
    void resetOutSequence_isStartedFalse() {
        Mockito.doNothing().when(fixClientTest).updateSessionSeqInSecondary(ArgumentMatchers.anyString());
        adminManagerTest.resetOutSequence("TDWL",1);
        Mockito.verify(adminManagerTest, Mockito.times(1)).updateSecondary(ArgumentMatchers.anyString());
    }

    @Test
    void resetOutSequence_isStartedTrue() throws Exception{
        Mockito.when(dfixRouterManagerTest.isStarted()).thenReturn(true);
        Mockito.doNothing().when(fixClientTest).setOutGoingSeqNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt());
        adminManagerTest.resetOutSequence("TDWL",1);
        Mockito.verify(fixClientTest, Mockito.times(1)).setOutGoingSeqNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt());
    }

    @Test
    void resetOutSequence_exceptionTest() throws Exception{
        Mockito.when(dfixRouterManagerTest.isStarted()).thenReturn(true);
        Mockito.doThrow(new IOException()).when(fixClientTest).setOutGoingSeqNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt());//For full coverage.

        Method reflectedMethod = adminManagerTest.getClass().getDeclaredMethod("showStatusHeader");
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Assertions.assertEquals(reflectedMethod.invoke(adminManagerTest).toString(), adminManagerTest.resetOutSequence("TDWL", 1), "Only Header should be return.");
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
    }

    @Test
    void resetInSequence_isStartedFalse() {
        Mockito.doNothing().when(fixClientTest).updateSessionSeqInSecondary(ArgumentMatchers.anyString());
        adminManagerTest.resetInSequence("TDWL",1);
        Mockito.verify(adminManagerTest, Mockito.times(1)).updateSecondary(ArgumentMatchers.anyString());
    }

    @Test
    void resetInSequence_isStartedTrue() throws Exception{
        Mockito.when(dfixRouterManagerTest.isStarted()).thenReturn(true);
        Mockito.doNothing().when(fixClientTest).setInComingSeqNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt());
        adminManagerTest.resetInSequence("TDWL",1);
        Mockito.verify(fixClientTest, Mockito.times(1)).setInComingSeqNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt());
    }

    @Test
    void resetInSequence_exceptionTest() throws Exception{
        Mockito.when(dfixRouterManagerTest.isStarted()).thenReturn(true);
        Mockito.doThrow(new IOException()).when(fixClientTest).setInComingSeqNo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt());//For full coverage.

        Method reflectedMethod = adminManagerTest.getClass().getDeclaredMethod("showStatusHeader");
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Assertions.assertEquals(reflectedMethod.invoke(adminManagerTest).toString(), adminManagerTest.resetInSequence("TDWL", 1), "Only Header should be return.");
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
    }

    @Test
    void disconnectSession_isStartedFalse() {
        adminManagerTest.disconnectSession("TDWL");
        Assertions.assertTrue(true);
    }

    @Test
    void disconnectSession_isStartedTrue() {
        Mockito.when(dfixRouterManagerTest.isStarted()).thenReturn(true);
        Mockito.doNothing().when(fixClientTest).disconnectSession(ArgumentMatchers.anyString());
        adminManagerTest.disconnectSession("TDWL");
        Mockito.verify(fixClientTest, Mockito.times(1)).disconnectSession(ArgumentMatchers.anyString());
    }

    @Test
    void connectSession_isStartedFalse() {
        adminManagerTest.connectSession("TDWL");
        Assertions.assertTrue(true);
    }

    @Test
    void connectSession_isStartedTrue() {
        Mockito.when(dfixRouterManagerTest.isStarted()).thenReturn(true);
        Mockito.doNothing().when(fixClientTest).connectSession(ArgumentMatchers.anyString());
        adminManagerTest.connectSession("TDWL");
        Mockito.verify(fixClientTest, Mockito.times(1)).connectSession(ArgumentMatchers.anyString());
    }

    @Test
    void runEod_isStartedFalse() {
        adminManagerTest.runEod("TDWL");
        Assertions.assertTrue(true);
    }

    @Test
    void runEod_isStartedTrue() {
        Mockito.when(dfixRouterManagerTest.isStarted()).thenReturn(true);
        Mockito.doNothing().when(fixClientTest).runEOD(ArgumentMatchers.anyString());
        adminManagerTest.runEod("TDWL");
        Mockito.verify(fixClientTest, Mockito.times(1)).runEOD(ArgumentMatchers.anyString());
    }

    @Test
    void sendSessionSequence_isLoggedFalse() {
        Assertions.assertNull(adminManagerTest.sendSessionSequence(), "If DFIXRouter is not started, Then we need to return null.");
    }

    @Test
    void sendSessionSequence_isLoggedTrue() {
        Mockito.when(dfixRouterManagerTest.isStarted()).thenReturn(true);
        SessionID initiator = new SessionID("FIX.4.2","INITIATOR","TDWL");
        SessionID acceptor = new SessionID("FIX.4.2","ACCEPTOR","TDWL");
        ArrayList initiatorList = new ArrayList();
        initiatorList.add(initiator);
        ArrayList acceptorList = new ArrayList();
        initiatorList.add(acceptor);
        Mockito.doReturn(initiatorList).when(fixClientTest).getInitiatorSessionList();
        Mockito.doReturn(acceptorList).when(fixClientTest).getAcceptorSessionList();
        Mockito.doReturn("INITIATOR").when(fixClientTest).getSessionProperty(initiator, IConstants.SESSION_IDENTIFIER);
        Mockito.doReturn(1).when(fixClientTest).getExpectedTargetSeqNumber(initiator);
        Mockito.doReturn(10).when(fixClientTest).getExpectedSenderSeqNumber(initiator);
        Mockito.doReturn("ACCEPTOR").when(fixClientTest).getSessionProperty(acceptor, IConstants.SESSION_IDENTIFIER);
        Mockito.doReturn(2).when(fixClientTest).getExpectedTargetSeqNumber(acceptor);
        Mockito.doReturn(20).when(fixClientTest).getExpectedSenderSeqNumber(acceptor);
        String sessionSequence = "SEQUENCE<INITIATOR:1:10><ACCEPTOR:2:20>";
        Assertions.assertEquals(sessionSequence, adminManagerTest.sendSessionSequence());
    }

    @Test
    void showStatus_flowTest() throws Exception{
        final ClassLoader classLoader = getClass().getClassLoader();
        final String dataDictionaryPath = classLoader.getResource("FIX42_TDWL.xml").getPath();
        final String cfgFileName = "correctSessions.cfg";
        final String path = classLoader.getResource(cfgFileName).getPath();
        final URI uri = new URI(path.trim().replaceAll("\\u0020", "%20"));
        final File file = new File(uri.getPath());
        final FileInputStream in = new FileInputStream(file);
        final SessionSettings sessionSettings = new SessionSettings(in);
        SessionID sessionID;
        HashMap<SessionID, SessionSettings> settings = new HashMap();
        Iterator iterator = sessionSettings.sectionIterator();
        while (iterator.hasNext()){
            sessionID = (SessionID) iterator.next();
            settings.put(sessionID, sessionSettings);
        }

        fixClientMockedStatic.when(FIXClient::getSettings).thenReturn(settings);
        final String simCfgFileName = "correctSessions_1.cfg";
        final String simPath = classLoader.getResource(simCfgFileName).getPath();
        final URI simUri = new URI(simPath.trim().replaceAll("\\u0020", "%20"));
        final File simFile = new File(simUri.getPath());
        final FileInputStream simIn = new FileInputStream(simFile);
        final SessionSettings simSessionSettings = new SessionSettings(simIn);
        fixClientMockedStatic.when(FIXClient::getSimSettings).thenReturn(simSessionSettings);
        settingsMockedStatic.when(Settings::isSimulatorOn).thenReturn(IConstants.CONSTANT_TRUE);
        Mockito.doReturn(1).when(fixClientTest).getExpectedTargetSeqNumber(Mockito.any());
        Mockito.doReturn(10).when(fixClientTest).getExpectedSenderSeqNumber(Mockito.any());
        Mockito.doReturn(new DataDictionary(dataDictionaryPath)).when(fixClientTest).getDataDictionary(Mockito.any());

        Method reflectedMethod = fixClientTest.getClass().getDeclaredMethod("loadCache");
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod.invoke(fixClientTest);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);

        Method reflectedMethod1 = fixClientTest.getClass().getDeclaredMethod("loadSimulatorCache");
        reflectedMethod1.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod1.invoke(fixClientTest);
        reflectedMethod1.setAccessible(IConstants.CONSTANT_FALSE);

        Mockito.doReturn(IConstants.STRING_SESSION_DOWN).when(fixClientTest).getSessionStatus(Mockito.any());
        String sessionStatus =
                                "Session               RemoteFirmID          LocalFirmID            Status         SeqNumIn  SeqNumOut \n" +
                                "------------------------------------------------------------------------------------------------------\n" +
                                "NSDQ1                 NSDQ1                 SLGM11                 DOWN           1         10        \n" +
                                "TDWL                  TDWL                  SLGM0                  DOWN           1         10        \n" +
                                "NSDQ1                 NSDQ1                 SLGM11                 DOWN           1         10        \n" +
                                "TDWL_CHAR_MORE_10     TDWL_CHAR_MORE_15     SLGM0_CHAR_MORE_15     DOWN           1         10        \n";
        Assertions.assertEquals(sessionStatus, adminManagerTest.showStatus());
    }

    @Test
    void startDFIXRouter_startedTrue() {
        Mockito.doReturn(true).when(dfixRouterManagerTest).isStarted();
        Assertions.assertEquals("DFIXRouter is already started.", adminManagerTest.startDFIXRouter());
    }

    @Test
    void startDFIXRouter_exceptionTest() throws DFIXConfigException {
        Mockito.doThrow(new DFIXConfigException("Mocked Exception")).when(dfixRouterManagerTest).startDFIXRouterManager();//For full coverage.
        Assertions.assertNull(adminManagerTest.startDFIXRouter(), "Null should be returned.");
    }

    @Test
    void startDFIXRouter_startedFalse_primaryNodeFalse() {
        String nodeName = "PrimaryNode";
        Mockito.doReturn(nodeName).when(dfixCluster).getPrimaryNode();
        Mockito.doReturn(false).when(dfixRouterManagerTest).isStarted();
        dfixRouterManagerMockedStatic.when(DFIXRouterManager::getDfixCluster).thenReturn(dfixCluster);
        Mockito.doReturn(false).when(dfixCluster).isAllowedToActivate();
        dfixCluster.setPrimaryNode(nodeName);
        Assertions.assertEquals("Not allowed to activate: Primary DFIXRTR is already running :" + nodeName, adminManagerTest.startDFIXRouter());
    }

    @Test
    void stopDFIXRouter_startedFalse() {
        Mockito.doReturn(false).when(dfixRouterManagerTest).isStarted();
        Assertions.assertEquals("DFIXRouter is already stopped.", adminManagerTest.stopDFIXRouter());
    }

    @Test
    void constructor_flowTest() throws Exception {
        Constructor<AdminManager> constructor = AdminManager.class.getDeclaredConstructor();
        Assertions.assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Test
    void getInstance_flowTest() {
        AdminManager.getInstance();//For full coverage.
        Assertions.assertTrue(AdminManager.getInstance() instanceof AdminManager, "Admin manager instance should be return.");
    }

    @Test
    void sendSessionList_flowTest() {
        fixClientMockedStatic.when(FIXClient::getSettings).thenReturn(new HashMap<>());
        Assertions.assertNotNull(adminManagerTest.sendSessionList());
    }
}
