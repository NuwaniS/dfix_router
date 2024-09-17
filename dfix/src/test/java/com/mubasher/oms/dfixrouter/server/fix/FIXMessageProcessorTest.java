package com.mubasher.oms.dfixrouter.server.fix;

import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;
import quickfix.Message;
import quickfix.field.ClOrdID;
import quickfix.field.OrderQty;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

class FIXMessageProcessorTest {

    @Spy
    FIXMessageProcessor fixMessageProcessor;

    @Test
    void getFixTagValue_FlowTest(){
        String clOrdId = "1";
        Message message = new Message();
        message.setString(ClOrdID.FIELD, clOrdId);
        Assertions.assertEquals(clOrdId, fixMessageProcessor.getFixTagValue(message.toString(), ClOrdID.FIELD), "Tag value has to be returned.");
        Assertions.assertNull(fixMessageProcessor.getFixTagValue(message.toString(), OrderQty.FIELD), "Tag value should not be returned.");
        Assertions.assertEquals("12", fixMessageProcessor.getFixTagValueOrDefault(message.toString(), OrderQty.FIELD, "12"), "default tag should be be returned.");
        Assertions.assertEquals("1", fixMessageProcessor.getFixTagValueOrDefault(message.toString(), ClOrdID.FIELD, "12"), "Tag value has to be returned.");
    }

    @Test
    public void getTenantCodeValue_FlowTest(){
        Message message = new Message();
        // tenantCode == null
        Assertions.assertEquals(IConstants.DEFAULT_TENANT_CODE, fixMessageProcessor.getTenantCode(message.toString()), "DEFAULT_TENANT_CODE value has to be returned.");
        Assertions.assertEquals(IConstants.DEFAULT_TENANT_CODE, fixMessageProcessor.getTenantCode(message), "DEFAULT_TENANT_CODE value has to be returned.");
        // tenantCode == "" ( also treated as null )
        message.setString(FixConstants.FIX_TAG_TENANT_CODE, "");
        Assertions.assertEquals(IConstants.DEFAULT_TENANT_CODE, fixMessageProcessor.getTenantCode(message.toString()), "DEFAULT_TENANT_CODE value has to be returned.");
        Assertions.assertEquals(IConstants.DEFAULT_TENANT_CODE, fixMessageProcessor.getTenantCode(message), "DEFAULT_TENANT_CODE value has to be returned.");
        // tenantCode == "TEST_1"
        message.setString(FixConstants.FIX_TAG_TENANT_CODE, "TEST_1");
        Assertions.assertEquals("TEST_1", fixMessageProcessor.getTenantCode(message.toString()), "Tenant code not match.");
        Assertions.assertEquals("TEST_1", fixMessageProcessor.getTenantCode(message), "Tenant code not match.");
        }

    @Test
    void getFixTagValue_nullTagTest(){
        String clOrdId = "1";
        StringBuilder sb = new StringBuilder();
        sb.append("=2").append(IConstants.FD);
        sb.append(ClOrdID.FIELD).append("=").append(clOrdId).append(IConstants.FD);
        Assertions.assertEquals(clOrdId, fixMessageProcessor.getFixTagValue(sb.toString(), ClOrdID.FIELD), "Tag value has to be returned.");
    }

    @Test
    void getFixTagValue_nonNumberTagTest(){
        String clOrdId = "1";
        StringBuilder sb = new StringBuilder();
        sb.append("string=2").append(IConstants.FD);
        sb.append(ClOrdID.FIELD).append("=").append(clOrdId).append(IConstants.FD);
        Assertions.assertEquals(clOrdId, fixMessageProcessor.getFixTagValue(sb.toString(), ClOrdID.FIELD), "Tag value has to be returned.");
    }

    @Test
    void constructor_flowTest() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<FIXMessageProcessor> constructor = FIXMessageProcessor.class.getDeclaredConstructor();
        Assertions.assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }
}
