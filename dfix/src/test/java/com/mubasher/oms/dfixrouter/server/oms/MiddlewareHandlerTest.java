package com.mubasher.oms.dfixrouter.server.oms;

import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.TextMessage;


/**
 * Created by Nilaan L on 8/1/2024.
 */
class MiddlewareHandlerTest {

    @Test
    void getStringMessage() throws JMSException {
        try (MockedStatic<Settings> settingsMockedStatic = Mockito.mockStatic(Settings.class)) {
            settingsMockedStatic.when(()->Settings.getProperty(IConstants.SETTING_DFIX_ID)).thenReturn(IConstants.SETTING_DFIX_ID);
            settingsMockedStatic.when(()->Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG)).thenReturn(IConstants.SETTING_NO);

            String text = "Hello World";
            MiddlewareHandler middlewareHandler = new MiddlewareHandler(new Host());
            TextMessage message = Mockito.mock(TextMessage.class);
            Mockito.when(message.getText()).thenReturn(text);
            final String textMessageString = middlewareHandler.getStringMessage(message);
            Assertions.assertEquals(text, textMessageString);

            MapMessage mapMessage = Mockito.mock(MapMessage.class);
            Mockito.when(mapMessage.getString(IConstants.MAP_EXCHANGE)).thenReturn("TDWL-CL");
            Mockito.when(mapMessage.getString(IConstants.MAP_MESSAGE)).thenReturn("Map Message");
            Mockito.when(mapMessage.getString(IConstants.MAP_EVENT_TYPE)).thenReturn("23041");
            Mockito.when(mapMessage.getString(IConstants.MAP_SEQUENCE)).thenReturn("Type1");

            final String mapMessageString = middlewareHandler.getStringMessage(mapMessage);
            Assertions.assertEquals("TDWL-CL\u001CType1\u001C23041\u001CMap Message\u001C", mapMessageString);

        }

    }


}
