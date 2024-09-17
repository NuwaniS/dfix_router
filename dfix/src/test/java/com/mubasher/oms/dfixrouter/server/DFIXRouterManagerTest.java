package com.mubasher.oms.dfixrouter.server;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.beans.License;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.server.admin.UIAdminServer;
import com.mubasher.oms.dfixrouter.server.admin.license.ExpiryDateChecker;
import com.mubasher.oms.dfixrouter.server.cluster.ClusterThread;
import com.mubasher.oms.dfixrouter.server.cluster.DFIXCluster;
import com.mubasher.oms.dfixrouter.server.exchange.FromExchangeQueue;
import com.mubasher.oms.dfixrouter.server.exchange.ToExchangeQueue;
import com.mubasher.oms.dfixrouter.server.exchange.clubbing.ClubbedMessageTimer;
import com.mubasher.oms.dfixrouter.server.exchange.clubbing.ExchangeExecutionMerger;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareListener;
import com.mubasher.oms.dfixrouter.server.oms.MiddlewareSender;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.WatchDogHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;

class DFIXRouterManagerTest {

    @Spy
    private DFIXRouterManager dfixRouterManagerTest;

    MockedStatic<Settings> settingsMockedStatic;
    MockedStatic<UIAdminServer> uiAdminServerMockedStatic;

    @BeforeEach
    public void setup() throws Exception {
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        uiAdminServerMockedStatic = Mockito.mockStatic(UIAdminServer.class);
        MockitoAnnotations.openMocks(this);
        DFIXRouterManager.setFromExchangeQueue(null);
        DFIXRouterManager.setToExchangeQueue(null);
        DFIXRouterManager.setDfixRouterStarted(false);
        Field dfixCluster = DFIXRouterManager.class.getDeclaredField("dfixCluster");
        dfixCluster.setAccessible(true);
        dfixCluster.set(null, null);
        dfixCluster.setAccessible(false);
        Field executor = DFIXRouterManager.class.getDeclaredField("executor");
        executor.setAccessible(true);
        executor.set(null, null);
        executor.setAccessible(false);
    }

    @AfterEach
    public void tearDown(){
        settingsMockedStatic.close();
        uiAdminServerMockedStatic.close();
    }
    private void makeExecutorAccessible() throws Exception {
        ScheduledExecutorService executorTest = Mockito.mock(ScheduledExecutorService.class);
        ScheduledFuture scheduledFuture = Mockito.mock(ScheduledFuture.class);
        Mockito.doReturn(scheduledFuture).when(executorTest).scheduleAtFixedRate(any(Runnable.class), Mockito.anyLong(), Mockito.anyLong(), any(TimeUnit.class));
        Mockito.doNothing().when(executorTest).shutdown();
        Field field = DFIXRouterManager.class.getDeclaredField("executor");
        field.setAccessible(true);
        field.set(null, executorTest);
    }
    private void initiateCluster() throws Exception {
        DFIXCluster dfixClusterTest = Mockito.mock(DFIXCluster.class);
        Mockito.doReturn(SettingsConstants.CLUSTER_MEMBER_PREFIX + 1).when(dfixClusterTest).getPrimaryNode();
        Field field = DFIXRouterManager.class.getDeclaredField("dfixCluster");
        field.setAccessible(true);
        field.set(null, dfixClusterTest);
}

    @Test
    void getInstance_flowTest() {
        Assertions.assertTrue(DFIXRouterManager.getInstance() instanceof DFIXRouterManager, "DFIXRouterManager instance should be returned.");
        DFIXRouterManager.getInstance();
    }

    @Test
    void runWatchDogAgent_disableWatchDogTest(){
        dfixRouterManagerTest.runWatchDogAgent();//Coverage test
    }

    @Test
    void runWatchDogAgent_flowTest() {
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG)).thenReturn(IConstants.SETTING_YES);
        try (MockedStatic<WatchDogHandler>  watchDogHandlerMockedStatic =  Mockito.mockStatic(WatchDogHandler.class)) {
            WatchDogHandler.runWatchdogAgent();
            dfixRouterManagerTest.runWatchDogAgent();
        }
    }

    @Test
    void startDFIXRouterManager_alreadyStartedTest() throws Exception {
        Mockito.when(dfixRouterManagerTest.isStarted()).thenReturn(true);
        Assertions.assertEquals("DFIXRouter is already started.", dfixRouterManagerTest.startDFIXRouterManager(), "Should return already started message.");
    }

    @Test
    void startDFIXRouterManager_primaryStartFlowTest() throws Exception {
        initiateCluster();
        Assertions.assertEquals("Not allowed to activate: Primary DFIXRTR is already running :" + DFIXRouterManager.getDfixCluster().getPrimaryNode(), dfixRouterManagerTest.startDFIXRouterManager(), "Should return started message.");
    }

    @Test
    void startDFIXRouterManager_withDfixClusterFlowTest() throws Exception {
        initiateCluster();
        makeExecutorAccessible();
        Mockito.doReturn(true).when(DFIXRouterManager.getDfixCluster()).isAllowedToActivate();
        Mockito.doNothing().when(DFIXRouterManager.getDfixCluster()).tellClusterMyStatus(Mockito.anyBoolean());
        Assertions.assertEquals("DFIXRouter Started", dfixRouterManagerTest.startDFIXRouterManager(), "Should return started message.");
    }

    @Test
    void startDFIXRouterManager_flowTest() throws Exception {
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.IS_CLUBBING_ENABLED)).thenReturn(IConstants.SETTING_YES);
        ArrayList<Host> hostList = new ArrayList<>();
        hostList.add(new Host());
        settingsMockedStatic.when(Settings::getHostList).thenReturn(hostList);
        Mockito.doNothing().when(dfixRouterManagerTest).startFromExchangeQueue();
        Mockito.doNothing().when(dfixRouterManagerTest).startToExchangeQueue();
        Mockito.doNothing().when(dfixRouterManagerTest).initExchangeExecutionClubbing();
        Assertions.assertEquals("DFIXRouter Started", dfixRouterManagerTest.startDFIXRouterManager(), "Should return started message.");
    }

    @Test
    void stopDFIXRouterManager_alreadyStoppedTest() {
        Assertions.assertEquals("DFIXRouter is already stopped.", dfixRouterManagerTest.stopDFIXRouterManager(), "Should return already stopped message.");
    }

    @Test
    void stopDFIXRouterManager_flowTest() throws Exception {
        makeExecutorAccessible();
        initiateCluster();
        try (MockedStatic<FIXClient> fixClientMockedStatic = Mockito.mockStatic(FIXClient.class)) {
            FIXClient fixClient = Mockito.mock(FIXClient.class);
            fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClient);
            Mockito.doNothing().when(fixClient).stopFIXSimulator();
            Mockito.doNothing().when(fixClient).stopFIXClient();
            Mockito.doReturn(true).when(dfixRouterManagerTest).isStarted();
            settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.IS_CLUBBING_ENABLED)).thenReturn(IConstants.SETTING_YES);
            settingsMockedStatic.when(Settings::isSimulatorOn).thenReturn(true);
            Mockito.doNothing().when(dfixRouterManagerTest).stopExchangeExecutionClubbing();
            Assertions.assertEquals("DFIXRouter Stopped.", dfixRouterManagerTest.stopDFIXRouterManager(), "Should return stopped message.");
            Mockito.verify(dfixRouterManagerTest, Mockito.times(1)).stopExchangeExecutionClubbing();
            Mockito.verify(dfixRouterManagerTest, Mockito.times(1)).setDfixRouterStarted(false);
            Assertions.assertFalse(dfixRouterManagerTest.isStarted(), "Should set the started boolean to false.");
        }
    }

    @Test
    void stopDFIXRouterManager_withFromExchangeQueueTest() throws Exception {
        makeExecutorAccessible();
        DFIXRouterManager.setDfixRouterStarted(true);
        FromExchangeQueue fromExchangeQueue = Mockito.mock(FromExchangeQueue.class);
        DFIXRouterManager.setFromExchangeQueue(fromExchangeQueue);
        Assertions.assertEquals("DFIXRouter Stopped.", dfixRouterManagerTest.stopDFIXRouterManager(), "Should return stopped message.");
        Mockito.verify(fromExchangeQueue, Mockito.times(1)).stopSenderQueueConnection();
    }

    @Test
    void stopDFIXRouterManager_withToExchangeQueueTest() throws Exception {
        makeExecutorAccessible();
        DFIXRouterManager.setDfixRouterStarted(true);
        ToExchangeQueue toExchangeQueue = Mockito.mock(ToExchangeQueue.class);
        DFIXRouterManager.setToExchangeQueue(toExchangeQueue);
        Assertions.assertEquals("DFIXRouter Stopped.", dfixRouterManagerTest.stopDFIXRouterManager(), "Should return stopped message.");
        Mockito.verify(toExchangeQueue, Mockito.times(1)).stopListenerQueueConnection();
    }

    @Test
    void isStarted_flowTest() {
        Assertions.assertFalse(dfixRouterManagerTest.isStarted(), "Default value should be false.");
    }

    @Test
    void getDfixCluster_flowTest() {
        Assertions.assertNull(DFIXRouterManager.getDfixCluster(), "Default value should be null.");
    }

    @Test
    void startToExchangeQueue_nullFlowTest() throws Exception {
        makeExecutorAccessible();
        dfixRouterManagerTest.startToExchangeQueue();
        Assertions.assertTrue(DFIXRouterManager.getToExchangeQueue() instanceof ToExchangeQueue, "ToExchangeQueue object should be returned.");
        startToExchangeQueue_flowTest();
    }

    public void startToExchangeQueue_flowTest() throws Exception {
        ToExchangeQueue toExchangeQueueTest = Mockito.mock(ToExchangeQueue.class);
        DFIXRouterManager.setToExchangeQueue(toExchangeQueueTest);
        Mockito.doNothing().when(toExchangeQueueTest).startListenerQueueConnection();
        dfixRouterManagerTest.startToExchangeQueue();
        Assertions.assertTrue(DFIXRouterManager.getToExchangeQueue() instanceof ToExchangeQueue, "ToExchangeQueue object should be returned.");
        DFIXRouterManager.setToExchangeQueue(null);
    }

    @Test
    void startFromExchangeQueue_nullFlowTest() throws Exception {
        makeExecutorAccessible();
        dfixRouterManagerTest.startFromExchangeQueue();
        Assertions.assertTrue(DFIXRouterManager.getFromExchangeQueue() instanceof FromExchangeQueue, "FromExchangeQueue object should be returned.");
        startFromExchangeQueue_flowTest();
    }

    public void startFromExchangeQueue_flowTest() throws Exception {
        FromExchangeQueue fromExchangeQueueTest = Mockito.mock(FromExchangeQueue.class);
        DFIXRouterManager.setFromExchangeQueue(fromExchangeQueueTest);
        Mockito.doNothing().when(fromExchangeQueueTest).startSenderQueueConnection();
        dfixRouterManagerTest.startFromExchangeQueue();
        Assertions.assertTrue(DFIXRouterManager.getFromExchangeQueue() instanceof FromExchangeQueue, "FromExchangeQueue object should be returned.");
    }

    @Test
    void setDfixRouterStarted_flowTest() {
        DFIXRouterManager.setDfixRouterStarted(true);
        Assertions.assertTrue(dfixRouterManagerTest.isStarted(), "Started should be static value and True should be returned.");
    }

    @Test
    void startClusterThread_nullFlowTest() throws Exception {
        initiateCluster();
        makeExecutorAccessible();
        dfixRouterManagerTest.startClusterThread();
        startClusterThread_flowTest();
    }

    public void startClusterThread_flowTest() {
        ClusterThread clusterThreadTest = Mockito.mock(ClusterThread.class);
        DFIXRouterManager.setClusterThread(clusterThreadTest);
        dfixRouterManagerTest.startClusterThread();
        DFIXRouterManager.setClusterThread(null);
    }

    @Test
    void initExchangeExecutionClubbing_flowTest() {
        ExchangeExecutionMerger exchangeExecutionMerger = Mockito.mock(ExchangeExecutionMerger.class);
        Mockito.doReturn(exchangeExecutionMerger).when(dfixRouterManagerTest).getExchangeExecutionMerger();
        Mockito.doNothing().when(exchangeExecutionMerger).start();
        ClubbedMessageTimer clubbedMessageTimer = Mockito.mock(ClubbedMessageTimer.class);
        Mockito.doReturn(clubbedMessageTimer).when(dfixRouterManagerTest).getClubbedMessageTimer();
        Mockito.doNothing().when(clubbedMessageTimer).start();
        dfixRouterManagerTest.initExchangeExecutionClubbing();
        Mockito.verify(exchangeExecutionMerger, Mockito.times(1)).init();
        Mockito.verify(exchangeExecutionMerger, Mockito.times(1)).start();
        Mockito.verify(clubbedMessageTimer, Mockito.times(1)).init();
        Mockito.verify(clubbedMessageTimer, Mockito.times(1)).start();
    }

    @Test
    void stopFromExchangeQueue_nullFlowTest(){
        dfixRouterManagerTest.stopFromExchangeQueue();
        stopFromExchangeQueue_flowTest();
    }

    public void stopFromExchangeQueue_flowTest() {
        final FromExchangeQueue fromExchangeQueueTest = Mockito.mock(FromExchangeQueue.class);
        DFIXRouterManager.setFromExchangeQueue(fromExchangeQueueTest);
        List<Map<String, MiddlewareSender>> senders = new ArrayList<>();
        fromExchangeQueueTest.setSenders(senders);
        Mockito.doNothing().when(fromExchangeQueueTest).stopSenderQueueConnection();
        dfixRouterManagerTest.stopFromExchangeQueue();
        Mockito.verify(fromExchangeQueueTest, Mockito.times(1)).stopSenderQueueConnection();
    }

    @Test
    void stopToExchangeQueue_nullFlowTest(){
        dfixRouterManagerTest.stopToExchangeQueue();
        stopToExchangeQueue_flowTest();
    }


    public void stopToExchangeQueue_flowTest() {
        final ToExchangeQueue toExchangeQueueTest = Mockito.mock(ToExchangeQueue.class);
        DFIXRouterManager.setToExchangeQueue(toExchangeQueueTest);
        List<MiddlewareListener>[] listeners = new ArrayList[0];
        toExchangeQueueTest.setListener(listeners);
        Mockito.doNothing().when(toExchangeQueueTest).stopListenerQueueConnection();
        dfixRouterManagerTest.stopToExchangeQueue();
        Mockito.verify(toExchangeQueueTest, Mockito.times(1)).stopListenerQueueConnection();
    }

    @Test
    void stopExchangeExecutionClubbing_FlowTest(){
        ScheduledExecutorService executorTest = Mockito.mock(ScheduledExecutorService.class);
        ExchangeExecutionMerger exchangeExecutionMergerTest = Mockito.mock(ExchangeExecutionMerger.class);
        Mockito.doReturn(executorTest).when(exchangeExecutionMergerTest).getExecutor();
        dfixRouterManagerTest.setExchangeExecutionMerger(exchangeExecutionMergerTest);
        dfixRouterManagerTest.stopExchangeExecutionClubbing();
        Assertions.assertTrue(dfixRouterManagerTest.getExchangeExecutionMerger() instanceof ExchangeExecutionMerger, "ExchangeExecutionMerger object should be returned.");
    }

    @Test
    void setClusterThread_FlowTest(){
        ClusterThread clusterThreadTest = Mockito.mock(ClusterThread.class);
        DFIXRouterManager.setClusterThread(clusterThreadTest);
    }

    @Test
    void setIsGraceFulClose_FlowTest() throws IllegalAccessException, NoSuchFieldException {
        Field reflectedField = DFIXRouterManager.class.getDeclaredField("isGraceFulClose");
        reflectedField.setAccessible(IConstants.CONSTANT_TRUE);
        Assertions.assertTrue((Boolean) reflectedField.get(DFIXRouterManager.class), "True should be returned.");

        DFIXRouterManager.setIsGraceFulClose(false);
        Assertions.assertFalse((Boolean) reflectedField.get(DFIXRouterManager.class), "False should be returned.");
        reflectedField.setAccessible(IConstants.CONSTANT_FALSE);

    }

    @Test
    void getClubbedMessageTimer_FlowTest(){
        Assertions.assertNull(dfixRouterManagerTest.getClubbedMessageTimer(), "Null should be returned.");
    }

    @Test
    void main_flowTest() throws Exception {
        String sysProbKey0 = "jgroups.tcpping.initial_hosts", sysPropVal0="127.0.0.1[7200],127.0.0.1[7201]";
        System.setProperty(sysProbKey0, sysPropVal0);
        ExpiryDateChecker expiryDateChecker = Mockito.mock(ExpiryDateChecker.class);
        License license = Mockito.mock(License.class);
        Field field = DFIXRouterManager.class.getDeclaredField("dfixrouterManager");
        field.setAccessible(true);
        field.set(null, dfixRouterManagerTest);
        try (MockedStatic<FIXClient> fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
             MockedStatic<DFIXCluster> dfixClusterMockedStatic = Mockito.mockStatic(DFIXCluster.class)
        ) {
            FIXClient fixClient = Mockito.mock(FIXClient.class);
            fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClient);
            dfixClusterMockedStatic.when(()->DFIXCluster.getInstance(Mockito.anyByte())).thenReturn(null);
            Mockito.doNothing().when(fixClient).startFIXClient();
            Mockito.doNothing().when(fixClient).startSimulator();
            settingsMockedStatic.when(Settings::isSimulatorOn).thenReturn(true);
            Mockito.doReturn(expiryDateChecker).when(dfixRouterManagerTest).validateLicense(Mockito.anyString(), Mockito.anyString());
            Mockito.doReturn(true).when(dfixRouterManagerTest).isFixCfgAvailable();
            Mockito.doReturn(license).when(expiryDateChecker).getLicense();
            Mockito.doReturn((byte) 2).when(license).getAllowedParallelDfixes();
            settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_PORT)).thenReturn("");
            DFIXRouterManager.main(null);
            System.getProperties().remove(sysProbKey0);
            field.set(null, null);
            field.setAccessible(false);
        }
    }

    @Test
    void main_withoutExpiryDateChecker() throws Exception {
        Field field = DFIXRouterManager.class.getDeclaredField("dfixrouterManager");
        field.setAccessible(true);
        field.set(null, dfixRouterManagerTest);
        FIXClient fixClient = Mockito.mock(FIXClient.class);
        try (MockedStatic<FIXClient> fixClientMockedStatic =  Mockito.mockStatic(FIXClient.class)) {
            fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClient);
            Mockito.doNothing().when(fixClient).startFIXClient();
            Mockito.doReturn(null).when(dfixRouterManagerTest).validateLicense(Mockito.anyString(), Mockito.anyString());
            Mockito.doReturn(true).when(dfixRouterManagerTest).isFixCfgAvailable();
            settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_PORT)).thenReturn("");
            DFIXRouterManager.main(null);
            field.set(null, null);
            field.setAccessible(false);
        }
    }

    @Test
    void main_withoutLicense() throws Exception {
        ExpiryDateChecker expiryDateChecker = Mockito.mock(ExpiryDateChecker.class);
        Field field = DFIXRouterManager.class.getDeclaredField("dfixrouterManager");
        field.setAccessible(true);
        field.set(null, dfixRouterManagerTest);
        FIXClient fixClient = Mockito.mock(FIXClient.class);
        try (MockedStatic<FIXClient> fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
             MockedStatic<DFIXCluster> dfixClusterMockedStatic = Mockito.mockStatic(DFIXCluster.class);
            ) {
            fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClient);
            dfixClusterMockedStatic.when(() -> DFIXCluster.getInstance(Mockito.anyByte())).thenReturn(null);
            Mockito.doReturn(expiryDateChecker).when(dfixRouterManagerTest).validateLicense(Mockito.anyString(), Mockito.anyString());
            Mockito.doReturn(true).when(dfixRouterManagerTest).isFixCfgAvailable();
            settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_PORT)).thenReturn("");
            DFIXRouterManager.main(null);
            field.set(null, null);
            field.setAccessible(false);
        }
    }

    @Test
    void main_withSingleInstanceLicense() throws Exception {
        ExpiryDateChecker expiryDateChecker = Mockito.mock(ExpiryDateChecker.class);
        License license = Mockito.mock(License.class);
        Field field = DFIXRouterManager.class.getDeclaredField("dfixrouterManager");
        field.setAccessible(true);
        field.set(null, dfixRouterManagerTest);
        FIXClient fixClient = Mockito.mock(FIXClient.class);
        try (MockedStatic<FIXClient> fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
             MockedStatic<DFIXCluster> dfixClusterMockedStatic = Mockito.mockStatic(DFIXCluster.class);
        ) {
            fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClient);
            dfixClusterMockedStatic.when(() -> DFIXCluster.getInstance(Mockito.anyByte())).thenReturn(null);
            Mockito.doReturn(expiryDateChecker).when(dfixRouterManagerTest).validateLicense(Mockito.anyString(), Mockito.anyString());
            Mockito.doReturn(true).when(dfixRouterManagerTest).isFixCfgAvailable();
            Mockito.doReturn(license).when(expiryDateChecker).getLicense();
            Mockito.doReturn((byte) 1).when(license).getAllowedParallelDfixes();
            settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_PORT)).thenReturn("");
            DFIXRouterManager.main(null);
            field.set(null, null);
            field.setAccessible(false);
        }
    }

    @Test
    void main_withSingleServer() throws Exception {
        String sysProbKey0 = "jgroups.tcpping.initial_hosts", sysPropVal0="127.0.0.1[7200]";
        System.setProperty(sysProbKey0, sysPropVal0);
        ExpiryDateChecker expiryDateChecker = Mockito.mock(ExpiryDateChecker.class);
        License license = Mockito.mock(License.class);
        Field field = DFIXRouterManager.class.getDeclaredField("dfixrouterManager");
        field.setAccessible(true);
        field.set(null, dfixRouterManagerTest);
        FIXClient fixClient = Mockito.mock(FIXClient.class);
        try (MockedStatic<FIXClient> fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
             MockedStatic<DFIXCluster> dfixClusterMockedStatic = Mockito.mockStatic(DFIXCluster.class);
        ) {
            fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClient);
            dfixClusterMockedStatic.when(() -> DFIXCluster.getInstance(Mockito.anyByte())).thenReturn(null);
            Mockito.doReturn(expiryDateChecker).when(dfixRouterManagerTest).validateLicense(Mockito.anyString(), Mockito.anyString());
            Mockito.doReturn(true).when(dfixRouterManagerTest).isFixCfgAvailable();
            Mockito.doReturn(license).when(expiryDateChecker).getLicense();
            Mockito.doReturn((byte) 2).when(license).getAllowedParallelDfixes();
            settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_PORT)).thenReturn("");
            DFIXRouterManager.main(null);
            System.getProperties().remove(sysProbKey0);
            field.set(null, null);
            field.setAccessible(false);
        }
    }

    @Test
    void main_withoutClusterIp() throws Exception {
        ExpiryDateChecker expiryDateChecker = Mockito.mock(ExpiryDateChecker.class);
        License license = Mockito.mock(License.class);
        Field field = DFIXRouterManager.class.getDeclaredField("dfixrouterManager");
        field.setAccessible(true);
        field.set(null, dfixRouterManagerTest);
        FIXClient fixClient = Mockito.mock(FIXClient.class);
        try (MockedStatic<FIXClient> fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
             MockedStatic<DFIXCluster> dfixClusterMockedStatic = Mockito.mockStatic(DFIXCluster.class);
        ) {
            fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClient);
            dfixClusterMockedStatic.when(() -> DFIXCluster.getInstance(Mockito.anyByte())).thenReturn(null);
            Mockito.doReturn(expiryDateChecker).when(dfixRouterManagerTest).validateLicense(Mockito.anyString(), Mockito.anyString());
            Mockito.doReturn(true).when(dfixRouterManagerTest).isFixCfgAvailable();
            Mockito.doReturn(license).when(expiryDateChecker).getLicense();
            Mockito.doReturn((byte) 2).when(license).getAllowedParallelDfixes();
            settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_PORT)).thenReturn("");
            DFIXRouterManager.main(null);
            field.set(null, null);
            field.setAccessible(false);
        }
    }

    @Test
    void main_withoutIsGraceFulClose() throws Exception {
        Field field = DFIXRouterManager.class.getDeclaredField("dfixrouterManager");
        field.setAccessible(true);
        field.set(null, dfixRouterManagerTest);
        DFIXRouterManager.setIsGraceFulClose(false);
        FIXClient fixClient = Mockito.mock(FIXClient.class);
        try (MockedStatic<FIXClient> fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
        ) {
            fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClient);
            Mockito.doReturn(null).when(dfixRouterManagerTest).validateLicense(Mockito.anyString(), Mockito.anyString());
            Mockito.doReturn(true).when(dfixRouterManagerTest).isFixCfgAvailable();
            settingsMockedStatic.when(() -> Settings.getProperty(SettingsConstants.CONSOLE_ADMIN_PORT)).thenReturn("");
            DFIXRouterManager.main(null);
            DFIXRouterManager.setIsGraceFulClose(true);
            field.set(null, null);
            field.setAccessible(false);
        }
    }
}
