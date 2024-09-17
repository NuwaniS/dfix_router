package com.mubasher.oms.dfixrouter.server.failover;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.cluster.DFIXCluster;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Created by Nilaan L on 7/11/2024.
 */
class FailOverHandlerTest {

    DFIXCluster primaryDfixClusterInstance;
    DFIXRouterManager dfixRouterManager;

    @Test
    void FailOverHandlerFlow_Test() {
        primaryDfixClusterInstance = Mockito.mock(DFIXCluster.class);
        dfixRouterManager = Mockito.mock(DFIXRouterManager.class);
        Mockito.when(primaryDfixClusterInstance.isAllowedToActivate()).thenReturn(IConstants.CONSTANT_FALSE);

        try (MockedStatic<Settings> settingsMockedStatic = Mockito.mockStatic(Settings.class);
             MockedStatic<DFIXCluster> clusterMockedStatic = Mockito.mockStatic(DFIXCluster.class);
             MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class)) {
            clusterMockedStatic.when(() -> DFIXCluster.getInstance()).thenReturn(primaryDfixClusterInstance);
            dfixRouterManagerMockedStatic.when(() -> DFIXRouterManager.getInstance()).thenReturn(dfixRouterManager);
            Mockito.doReturn("").when(dfixRouterManager).startDFIXRouterManager();
            settingsMockedStatic.when(() -> Settings.getInt(SettingsConstants.FAILOVER_ATTEMPTS)).thenReturn(IConstants.CONSTANT_THREE_3);
            settingsMockedStatic.when(() -> Settings.getInt(SettingsConstants.FAILOVER_INTERVAL)).thenReturn(IConstants.CONSTANT_TEN_10);
            FailOverHandler failOverHandler = new FailOverHandler();

            Assertions.assertEquals(IConstants.CONSTANT_THREE_3, failOverHandler.getFailoverAttempts());
            Assertions.assertEquals(IConstants.CONSTANT_TEN_10, failOverHandler.getFailoverInterval());

            failOverHandler.start();
            failOverHandler.join();
            Mockito.verify(primaryDfixClusterInstance, Mockito.times(IConstants.CONSTANT_ONE_1)).askWhoIsPrimary();
            Mockito.verify(dfixRouterManager, Mockito.times(IConstants.CONSTANT_ONE_1)).startDFIXRouterManager();
        } catch (DFIXConfigException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
