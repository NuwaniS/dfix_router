package com.mubasher.oms.dfixrouter.server.fix;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import quickfix.SessionID;
import quickfix.SessionSettings;

import java.util.Properties;

import static com.mubasher.oms.dfixrouter.server.fix.FIXApplicationCommonLogic.getExecutionId;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by Nilaan L on 7/23/2024.
 */
class FIXApplicationCommonLogicTest {

    @Mock
    SessionSettings sessionSettings;

    @Spy
    FIXApplicationCommonLogic fixApplicationCommonLogic;

    SessionID sessionID;
    DFIXMessage msg;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        sessionID = new SessionID("FIX.4.2", "SLGM0", "TDWL");
        msg = new DFIXMessage();
        msg.setExchange("TDWL-CL");
        msg.setSequence(getExecutionId());
        msg.setMessage("LOGGED_INTO_EXCHANGE");
    }

    @Test
    void addCustomFieldsToOMS_Without_User_Defined_Test() throws Exception {
        final String message = (String) TestUtils.invokePrivateMethod(fixApplicationCommonLogic, "addCustomFieldsToOMS", new Class[]{SessionSettings.class, String.class, SessionID.class}, sessionSettings, msg.getMessage(), sessionID);
        assertEquals(msg.getMessage(), message);
    }

    @Test
    void addCustomFieldsToOMS_With_User_Defined_Test() throws Exception {
        Properties props = new Properties();
        props.setProperty(IConstants.SETTING_USER_DEFINED_TO_OMS_TAGS,"7999=DEFAULT_TENANT|8000=8001");
        Mockito.when(sessionSettings.getSessionProperties(sessionID,IConstants.CONSTANT_TRUE)).thenReturn(props);

        final String message = (String) TestUtils.invokePrivateMethod(fixApplicationCommonLogic, "addCustomFieldsToOMS", new Class[]{SessionSettings.class, String.class, SessionID.class}, sessionSettings, msg.getMessage(), sessionID);
        assertEquals(msg.getMessage()+"\u00017999=DEFAULT_TENANT\u00018000=8001\u0001", message);
    }
}
