package com.mubasher.oms.dfixrouter.server.fix;

import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import quickfix.Message;

import java.util.StringTokenizer;

public class FIXMessageProcessor {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.FIXMessageProcessor");

    private FIXMessageProcessor() {
        super();
    }

    public static String getFixTagValue(String sFixMessage, int fixConstant) {
        String sToken;
        String sTag;
        int iTag;
        String sValue = null;
        StringTokenizer st = new StringTokenizer(sFixMessage, IConstants.FD);
        StringTokenizer st1;

        while (st.hasMoreTokens()) {
            sTag = null;
            sToken = st.nextToken();
            st1 = new StringTokenizer(sToken, "=");
            if (st1.countTokens() == 2) {
                sTag = st1.nextToken();
            }
            if (sTag != null) {
                try {
                    iTag = Integer.parseInt(sTag);
                    if (iTag == fixConstant) {
                        sValue = st1.nextToken();
                        break;
                    }
                } catch (NumberFormatException e) {
                    logger.error("Error while format fix tag: " + sTag);
                }
            }
        }
        return sValue;
    }
    public static String getFixTagValueOrDefault(String sFixMessage, int fixConstant,String defaultVal) {
        final String fixTagValue = getFixTagValue(sFixMessage, fixConstant);
        return fixTagValue == null ? defaultVal : fixTagValue;
    }

    public static String getTenantCode(String sFixMessage) {
        String tenantCode = getFixTagValue(sFixMessage, FixConstants.FIX_TAG_TENANT_CODE);
        if (tenantCode == null || tenantCode.isEmpty()) {
            tenantCode = IConstants.DEFAULT_TENANT_CODE;
        }
        return tenantCode;
    }

    public static String getTenantCode(Message message) {
        String tenantCode = IConstants.DEFAULT_TENANT_CODE;
        try {
            if (message.isSetField(FixConstants.FIX_TAG_TENANT_CODE) && !message.getString(FixConstants.FIX_TAG_TENANT_CODE).isEmpty()) {
                tenantCode = message.getString(FixConstants.FIX_TAG_TENANT_CODE);
            }
        } catch (Exception e) {
            logger.error("Error while resolving tenant code: " + message.toString());
        }
        return tenantCode;
    }
}
