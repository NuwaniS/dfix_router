package com.mubasher.oms.dfixrouter.server.fix;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import quickfix.*;
import quickfix.Message;
import quickfix.MessageCracker;
import quickfix.field.*;
import quickfix.fix42.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

public class FIXApplicationCommonLogic extends MessageCracker {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.FIXApplicationCommonLogic");
    private static final String EXECUTION_ID_PREFIX = new SimpleDateFormat("yyyyMMddHHmm").format(new Date());
    private static int executionId = 0;

    protected void addCustomFields(SessionSettings sessionSettings, Message message, SessionID sessionID) throws ConfigError {
        String logonString;
        Properties prop = sessionSettings.getSessionProperties(sessionID, true);
        if (prop.containsKey(IConstants.SETTING_USER_DEFINED_TAGS)) {
            logonString = prop.get(IConstants.SETTING_USER_DEFINED_TAGS).toString();
            StringTokenizer tags = new StringTokenizer(logonString, "|");
            while (tags.hasMoreTokens()) {
                String[] keyValue = tags.nextToken().split("=");
                int key = Integer.parseInt(keyValue[0]);
                message.setField(new StringField(key, keyValue[1]));
            }
        }
    }

    protected void addCustomFieldsToOMS(SessionSettings sessionSettings, Message message, SessionID sessionID) {
        String userDefinedString;
        try {
            Properties prop = sessionSettings.getSessionProperties(sessionID, true);
            if (prop.containsKey(IConstants.SETTING_USER_DEFINED_TO_OMS_TAGS)) {
                userDefinedString = prop.get(IConstants.SETTING_USER_DEFINED_TO_OMS_TAGS).toString();
                StringTokenizer tags = new StringTokenizer(userDefinedString, "|");
                while (tags.hasMoreTokens()) {
                    String[] keyValue = tags.nextToken().split("=");
                    int key = Integer.parseInt(keyValue[0]);
                    message.setField(new StringField(key, keyValue[1]));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to add custom tags to OMS", e);
        }

        try {
            Properties prop = sessionSettings.getSessionProperties(sessionID, true);
            if (prop.containsKey(IConstants.SETTING_USER_DEFINED_HEADER_TO_OMS_TAGS)) {
                userDefinedString = prop.get(IConstants.SETTING_USER_DEFINED_HEADER_TO_OMS_TAGS).toString();
                StringTokenizer tags = new StringTokenizer(userDefinedString, "|");
                while (tags.hasMoreTokens()) {
                    String[] keyValue = tags.nextToken().split("=");
                    int key = Integer.parseInt(keyValue[0]);
                    message.getHeader().setField(new StringField(key, keyValue[1]));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to add custom header tags to OMS", e);
        }
    }

    protected String addCustomFieldsToOMS(SessionSettings sessionSettings, String message, SessionID sessionID) {
        String userDefinedString;
        try {
            Properties prop = sessionSettings.getSessionProperties(sessionID, true);
            if (prop.containsKey(IConstants.SETTING_USER_DEFINED_TO_OMS_TAGS)) {
                StringBuilder sb = new StringBuilder();
                userDefinedString = prop.get(IConstants.SETTING_USER_DEFINED_TO_OMS_TAGS).toString();
                StringTokenizer tags = new StringTokenizer(userDefinedString, "|");
                sb.append(message).append(IConstants.FD);
                while (tags.hasMoreTokens()) {
                    sb.append(tags.nextToken()).append(IConstants.FD);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            logger.error("Failed to add custom tags to OMS", e);
        }
        return message;
    }

    protected boolean sendToTarget(Message message, SessionID sessionId) {
        boolean status = false;
        if (sessionId != null && message != null) {
            try {
                Session.sendToTarget(message, sessionId);
                status = true;
            } catch (SessionNotFound sessionNotFound) {
                logger.error("Exception at sendToTarget" + sessionNotFound.getMessage(), sessionNotFound);
            }
        }
        return status;
    }

    protected Message getRejectMessage(Message order, String rejectReason) {
        Message reject = new Message();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
        try {
            if (order.getHeader().getString(MsgType.FIELD).equals(NewOrderSingle.MSGTYPE)) {
                reject = new ExecutionReport();
                reject.setChar(OrdStatus.FIELD, OrdStatus.REJECTED);
                reject.setString(OrderID.FIELD, order.getString(ClOrdID.FIELD));
                if (order.isSetField(Price.FIELD)) {
                    reject.setDouble(AvgPx.FIELD, order.getDouble(Price.FIELD));
                }
                reject.setDouble(CumQty.FIELD, order.isSetField(CumQty.FIELD) ? order.getDouble(CumQty.FIELD) : 0);
                reject.setString(ExecID.FIELD, getExecutionId());
                reject.setChar(ExecTransType.FIELD, ExecTransType.NEW);
                reject.setDouble(OrderQty.FIELD, order.getDouble(OrderQty.FIELD));
                if (order.isSetField(OrdType.FIELD)) {
                    reject.setChar(OrdType.FIELD, order.getChar(OrdType.FIELD));
                }
                reject.setChar(Side.FIELD, order.getChar(Side.FIELD));
                reject.setString(Symbol.FIELD, order.getString(Symbol.FIELD));
                reject.setChar(ExecType.FIELD, ExecType.REJECTED);
                reject.setDouble(LeavesQty.FIELD, order.isSetField(LeavesQty.FIELD) ? order.getDouble(LeavesQty.FIELD) : 0);
            } else if (order.getHeader().getString(MsgType.FIELD).equals(OrderCancelRequest.MSGTYPE)
                    || order.getHeader().getString(MsgType.FIELD).equals(OrderCancelReplaceRequest.MSGTYPE)) {
                reject = new OrderCancelReject();
                reject.setChar(CxlRejResponseTo.FIELD, order.getHeader().getString(MsgType.FIELD).equals(OrderCancelRequest.MSGTYPE) ? '1' : '2');
                reject.setChar(OrdStatus.FIELD, OrdStatus.NEW);
                reject.setString(OrderID.FIELD, "NONE");
            }
            reject.setString(TransactTime.FIELD, sdf.format(new Date()));
            reject.setString(SendingTime.FIELD, sdf.format(new Date()));
            reject.setString(ClOrdID.FIELD, order.getString(ClOrdID.FIELD));
            reject.setString(CxlRejReason.FIELD, "-1");
            if (order.isSetField(OrigClOrdID.FIELD)) {
                reject.setString(OrigClOrdID.FIELD, order.getString(OrigClOrdID.FIELD));
            }
            reject.setString(Text.FIELD, rejectReason);
        } catch (FieldNotFound fieldNotFound) {
            logger.error("Error at Reject Message Creation: " + fieldNotFound.getMessage(), fieldNotFound);
        }
        return reject;
    }

    public static String getExecutionId() {
        StringBuilder sb = new StringBuilder();
        sb.append(EXECUTION_ID_PREFIX).append("_").append(++executionId);
        return sb.toString();
    }

    public int generateID(String key) {
        int id = 0;
        for (char c : key.toCharArray()) {
            id += c;
        }
        return id;
    }
}
