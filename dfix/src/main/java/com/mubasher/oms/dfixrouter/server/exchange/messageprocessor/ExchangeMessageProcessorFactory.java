package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor;

import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic.DefaultExchangeMessageProcessor;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic.KSEExchangeMessageProcessor;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic.MSMExchangeMessageProcessor;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic.TDWLExchangeMessageProcessor;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.ExecType;
import quickfix.field.MsgType;
import quickfix.field.OrdStatus;

public class ExchangeMessageProcessorFactory {

    private ExchangeMessageProcessorFactory() {
    }

    public static boolean processMessage(String exchange, String message) {
        return getExchangeMessageProcessor(exchange).processMessage(message);
    }

    private static ExchangeMessageProcessorI getExchangeMessageProcessor(String exchange) {
        switch (exchange.toUpperCase()) {
            case ("KSE"):
                return KSEExchangeMessageProcessor.getInstance();
            case ("MSM"):
                return MSMExchangeMessageProcessor.getInstance();
            case ("TDWL"):
                return TDWLExchangeMessageProcessor.getInstance();
            default:
                return DefaultExchangeMessageProcessor.getInstance();
               /* A DefaultExchangeMessageProcessor class has been introduced here without sending a reference from the interface
               because a new reference is created each time this block is called*/
        }
    }

    public static void regenLogOnMsg(Message message, SessionID sessionID, String exchange) {
        getExchangeMessageProcessor(exchange).regenLogOnMsg(message,sessionID,exchange);
    }

    public static boolean isAllowedParallelProcessing(Message message, String exchange) throws FieldNotFound {
        boolean isAllowedParallelProcessing = false;
        if (message.getHeader().getString(MsgType.FIELD).equals(MsgType.EXECUTION_REPORT)
                && message.getChar(OrdStatus.FIELD) == OrdStatus.NEW
                && message.getChar(ExecType.FIELD) == ExecType.NEW) {
            isAllowedParallelProcessing = getExchangeMessageProcessor(exchange).isAllowedParallelProcessing(message);
        }
        return isAllowedParallelProcessing;
    }

    public static boolean isIgnoreDropCopyMessage(Message message, String exchange, SessionID sessionID) {
        return getExchangeMessageProcessor(exchange).isIgnoreDropCopyMessage(message, sessionID);
    }
}
