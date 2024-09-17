package com.mubasher.oms.dfixrouter.server.system;

import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SettingsTest {
    private static  MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic;
    private static final String RUN_TIME_LOCATION = System.getProperty("base.dir") + "/src/main/external-resources";
    private static final String RUN_TIME_CONFIG_LOCATION =  RUN_TIME_LOCATION + "/config/settings.ini";

    @BeforeEach
    public void setup() {
        dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);
    }

    @AfterEach
    public void tearDown() {
        dfixRouterManagerMockedStatic.close();
    }

    @Test
    void loadPassTest() {
        Settings.load(RUN_TIME_CONFIG_LOCATION);
        Assertions.assertEquals(Settings.getInt(SettingsConstants.HOSTS_COUNT), Settings.getHostList().size());
    }

    @Test
    void loadFail1Test() {
        Settings.load(null);
        dfixRouterManagerMockedStatic.verify(
                () -> DFIXRouterManager.exitApplication(Mockito.anyInt()),
                Mockito.times(2)
        );
    }
    @Test
    public void loadFail2Test() {
        Settings.load();
        dfixRouterManagerMockedStatic.verify(
                () -> DFIXRouterManager.exitApplication(Mockito.anyInt()),
                Mockito.times(2)
        );
    }

    @Test
    void getPropertyFailTest(){
        Assertions.assertEquals("", Settings.getProperty(null));
    }

    @Test
    void getStringFailTest() {
        Assertions.assertNull(Settings.getString(null));
    }

    @Test
    void getIntFailTest(){
        Assertions.assertEquals(0, Settings.getInt(null));
    }

    @Test
    void getHostIdForOmsId_CorrectOMSIdTest() {
        int omsId = 1;  //One host is configured in the settings.ini file
        Settings.load(RUN_TIME_CONFIG_LOCATION);
        int hostId = Settings.getHostIdForOmsId(omsId);
        if (Settings.getInt(SettingsConstants.HOSTS_COUNT) > 0) {
            Assertions.assertEquals(1, hostId, "Expected host id = 1");
        }
    }

    @Test
    void getHostIdForOmsId_InCorrectOMSIdTest() {
        int omsId = 0;  //One host is configured in the settings.ini file
        Settings.load(RUN_TIME_CONFIG_LOCATION);
        int hostId = Settings.getHostIdForOmsId(omsId);

        Assertions.assertEquals(-1, hostId, "Expected host id = -1 when no mapping found");
    }
}
