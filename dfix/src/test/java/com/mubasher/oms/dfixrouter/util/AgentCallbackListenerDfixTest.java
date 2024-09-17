package com.mubasher.oms.dfixrouter.util;

import com.dfn.watchdog.commons.ClusterConfiguration;
import com.dfn.watchdog.commons.ClusterServerConfig;
import com.dfn.watchdog.commons.NodeType;
import com.dfn.watchdog.commons.messages.cluster.ClusterUpdate;
import com.dfn.watchdog.commons.messages.cluster.RegistrationAck;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;

/**
 * Created by Nilaan L on 7/23/2024.
 */
class AgentCallbackListenerDfixTest {
    private static final String RUN_TIME_CONFIG_LOCATION =  System.getProperty("base.dir") + "/src/main/external-resources/config/settings.ini";

    private static MockedStatic<Settings> settingsMockedStatic;
    private static MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic;
    @Mock
    DFIXRouterManager dfixRouterManagerMock;

    @BeforeAll
    static void setup() {
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dfixRouterManagerMockedStatic.when(DFIXRouterManager::getInstance).thenReturn(dfixRouterManagerMock);
    }

    @AfterAll
    static void tearDown() {
        settingsMockedStatic.close();
        dfixRouterManagerMockedStatic.close();
    }

    @Test
    void updateClusterConfiguration_Test() throws DFIXConfigException {

        //Add Host
        List<Host> hostList = new ArrayList<>();
        Map<Integer, Integer> hostIdMap = new HashMap<>();
        settingsMockedStatic.when(()->Settings.getHostList()).thenReturn(hostList);
        settingsMockedStatic.when(()->Settings.getHostIdMap()).thenReturn(hostIdMap);

        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.ENABLE_FALCON_REGISTRATION)).thenReturn(IConstants.SETTING_YES);
        settingsMockedStatic.when(()->Settings.getInt(SettingsConstants.HOSTS_COUNT)).thenReturn(IConstants.CONSTANT_TWO_2);


        Settings.load(RUN_TIME_CONFIG_LOCATION);
        AgentCallbackListenerDfix agentCallbackListener = new AgentCallbackListenerDfix();
        ClusterServerConfig clusterServerConfig = new ClusterServerConfig();
        clusterServerConfig.setIp("192.168.14.24");
        clusterServerConfig.setNodeId((short) IConstants.CONSTANT_ONE_1);
        clusterServerConfig.setPort(80);
        clusterServerConfig.setNodeType(NodeType.OMS);
        ClusterUpdate clusterUpdate = new ClusterUpdate(clusterServerConfig,IConstants.CONSTANT_TRUE);
        agentCallbackListener.updateClusterConfiguration(clusterUpdate);

        Mockito.verify(dfixRouterManagerMock, Mockito.times(1)).startFromExchangeQueue(any(Host.class));
        Mockito.verify(dfixRouterManagerMock, Mockito.times(1)).startToExchangeQueue(any(Host.class));
        Assertions.assertEquals(IConstants.CONSTANT_ONE_1, Settings.getHostList().size());
        Assertions.assertTrue(Settings.getHostIdMap().containsKey(IConstants.CONSTANT_ONE_1));

        //Add second Host
        clusterServerConfig.setIp("192.168.14.25");
        clusterServerConfig.setNodeId((short) IConstants.CONSTANT_TWO_2);
        clusterServerConfig.setPort(80);
        clusterServerConfig.setNodeType(NodeType.OMS);
        clusterUpdate = new ClusterUpdate(clusterServerConfig,IConstants.CONSTANT_TRUE);
        agentCallbackListener.updateClusterConfiguration(clusterUpdate);
        Assertions.assertEquals(IConstants.CONSTANT_TWO_2, Settings.getHostList().size());
        Assertions.assertTrue(Settings.getHostIdMap().containsKey(IConstants.CONSTANT_TWO_2));
        Assertions.assertTrue(Settings.getHostIdMap().containsKey(IConstants.CONSTANT_ONE_1));

        //Remove Host
        clusterUpdate = new ClusterUpdate(clusterServerConfig,IConstants.CONSTANT_FALSE);
        agentCallbackListener.updateClusterConfiguration(clusterUpdate);
        Assertions.assertEquals(IConstants.CONSTANT_ONE_1, Settings.getHostList().size());
        Assertions.assertFalse(Settings.getHostIdMap().containsKey(IConstants.CONSTANT_TWO_2));
        Assertions.assertTrue(Settings.getHostIdMap().containsKey(IConstants.CONSTANT_ONE_1));
    }
    @Test
    void updateConfigurationRegistration_Test() {
        List<Host> hostList = new ArrayList<>();
        Map<Integer, Integer> hostIdMap = new HashMap<>();
        settingsMockedStatic.when(()->Settings.getHostList()).thenReturn(hostList);
        settingsMockedStatic.when(()->Settings.getHostIdMap()).thenReturn(hostIdMap);

        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.ENABLE_FALCON_REGISTRATION)).thenReturn(IConstants.SETTING_YES);
        Mockito.when(dfixRouterManagerMock.isStarted()).thenReturn(IConstants.CONSTANT_FALSE);

        RegistrationAck registrationAck = new RegistrationAck();
        registrationAck.setNodeId((short) IConstants.CONSTANT_ONE_1);
        ClusterConfiguration clusterConfig = new ClusterConfiguration();

        ClusterServerConfig clusterServerConfig1 = new ClusterServerConfig();
        clusterServerConfig1.setIp("192.168.14.24");
        clusterServerConfig1.setNodeId((short) IConstants.CONSTANT_ONE_1);
        clusterServerConfig1.setPort(80);
        clusterServerConfig1.setNodeType(NodeType.OMS);

        ClusterServerConfig clusterServerConfig2 = new ClusterServerConfig();
        clusterServerConfig2.setIp("192.168.14.25");
        clusterServerConfig2.setNodeId((short) IConstants.CONSTANT_TWO_2);
        clusterServerConfig2.setPort(80);
        clusterServerConfig2.setNodeType(NodeType.OMS);

        clusterConfig.getOmsList().add(clusterServerConfig1);
        clusterConfig.getOmsList().add(clusterServerConfig2);
        registrationAck.setClusterConfiguration(clusterConfig);

        AgentCallbackListenerDfix agentCallbackListener = new AgentCallbackListenerDfix();
        agentCallbackListener.updateConfigurationRegistration(registrationAck);

        Assertions.assertEquals(IConstants.CONSTANT_TWO_2, Settings.getHostList().size());
        Assertions.assertTrue(Settings.getHostIdMap().containsKey(IConstants.CONSTANT_TWO_2));
        Assertions.assertTrue(Settings.getHostIdMap().containsKey(IConstants.CONSTANT_ONE_1));
    }
}
