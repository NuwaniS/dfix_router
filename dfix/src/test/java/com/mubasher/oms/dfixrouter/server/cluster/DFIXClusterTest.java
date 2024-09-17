package com.mubasher.oms.dfixrouter.server.cluster;

import com.mubasher.oms.dfixrouter.beans.ClusterMessage;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import org.jgroups.*;
import org.jgroups.util.NameCache;
import org.jgroups.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Created by Nilaan L on 7/10/2024.
 */
class DFIXClusterTest {

    DFIXCluster primaryDfixClusterInstance;
    MockedStatic<Settings> settingsMockedStatic;
    MockedStatic<FIXClient> fixClientMockedStatic;
    @Mock
    FIXClient fixClientMock;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this);
        // reset singleton
        TestUtils.changeFieldAccessibility(DFIXCluster.class,"dfixCluster",true).set(null,null);
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);
        fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClientMock);
        settingsMockedStatic.when(()->Settings.getProperty(IConstants.SETTING_DFIX_ID)).thenReturn(IConstants.STRING_1);
        settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.CLUSTER_NAME_DFIX)).thenReturn("DFIX_CLUSTER"+System.currentTimeMillis()); //timestamp added to avoid interference from any previous JGroup related tests
        primaryDfixClusterInstance = Mockito.spy(DFIXCluster.getInstance((byte) 2));
    }

    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
        settingsMockedStatic.close();
        fixClientMockedStatic.close();

        // reset singleton
        primaryDfixClusterInstance.disconnect();
        TestUtils.changeFieldAccessibility(DFIXCluster.class,"dfixCluster",true).set(null,null);
    }

    @Test
    void DFIXClusterTest_Flow_Test() {
            // verify view update received
            Assertions.assertTrue(DFIXCluster.getPreviousNodeList().stream().map(m->m.toString()).collect(Collectors.toList()).contains("DFIX1"));
            Assertions.assertTrue(primaryDfixClusterInstance.isAllowedToActivate());
            Assertions.assertTrue(primaryDfixClusterInstance.isSingleNodeYet());

            doReturn(false).when(primaryDfixClusterInstance).isSingleNodeYet();
            primaryDfixClusterInstance.tellClusterMyStatus(IConstants.CONSTANT_TRUE);
            Assertions.assertEquals("DFIX1",primaryDfixClusterInstance.getPrimaryNode());

            primaryDfixClusterInstance.tellClusterMyStatus(IConstants.CONSTANT_FALSE);
            Assertions.assertEquals("",primaryDfixClusterInstance.getPrimaryNode());

            //clean
            primaryDfixClusterInstance.disconnect();
    }

    @Test
    void receive_Test() {

        //case: PRIMARY_STATUS_CHANGE
        //Current primary is DFIX1 and receiving primary message from DFIX1 where status is false
        primaryDfixClusterInstance.setPrimaryNode("DFIX1");
        ClusterMessage clusterMessageWhoIsPrimary = new ClusterMessage(ClusterMessage.TYPE.PRIMARY_STATUS_CHANGE); //primaryStatus  false
        Message msg = new Message(null, clusterMessageWhoIsPrimary);
        final UUID srcAdd = UUID.fromString("91705d6d-63b0-49b0-86eb-757360d2b81d");
        NameCache.add(srcAdd, "DFIX1");
        msg.setSrc(srcAdd);

        primaryDfixClusterInstance.receive(msg);
        Assertions.assertEquals("",primaryDfixClusterInstance.getPrimaryNode());

        //primary is DFIX2 and receiving primary message from DFIX1 where status is true
        primaryDfixClusterInstance.setPrimaryNode("DFIX2");
        ClusterMessage clusterMessageWhoIsPrimary1 = new ClusterMessage(ClusterMessage.TYPE.PRIMARY_STATUS_CHANGE,IConstants.CONSTANT_TRUE);
        msg.setObject(clusterMessageWhoIsPrimary1);
        primaryDfixClusterInstance.receive(msg);
        Assertions.assertEquals("DFIX1",primaryDfixClusterInstance.getPrimaryNode());

        //case: WHO_IS_PRIMARY
        primaryDfixClusterInstance.tellClusterMyStatus(IConstants.CONSTANT_TRUE);
        ClusterMessage clusterMessagePrimaryStatusChange = new ClusterMessage(ClusterMessage.TYPE.WHO_IS_PRIMARY, IConstants.CONSTANT_TRUE);
        msg.setObject(clusterMessagePrimaryStatusChange);
        primaryDfixClusterInstance.receive(msg);
        Mockito.verify(primaryDfixClusterInstance,Mockito.times(2)).tellClusterMyStatus(IConstants.CONSTANT_TRUE);

        //case: REFRESH_OWN_STATUS
        ClusterMessage clusterMessageRefreshOwnStatus = new ClusterMessage(ClusterMessage.TYPE.REFRESH_OWN_STATUS, "100");
        msg.setObject(clusterMessageRefreshOwnStatus);
        primaryDfixClusterInstance.receive(msg);
        verify(fixClientMock,Mockito.times(1)).updateSessionSeqInSecondary(anyString());
    }
}
