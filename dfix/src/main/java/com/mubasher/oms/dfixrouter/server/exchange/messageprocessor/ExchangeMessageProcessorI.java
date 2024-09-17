package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor;

import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.ExecType;
import quickfix.field.OrdStatus;

public interface ExchangeMessageProcessorI {
    default boolean processMessage(String message) {
        return true;
    }

    default void regenLogOnMsg(Message message, SessionID sessionID, String exchange) {
    }

    default boolean isAllowedParallelProcessing(Message message) {
        boolean isAllowedParallelProcessing = false;
        try {
            isAllowedParallelProcessing = (message.getChar(OrdStatus.FIELD) == OrdStatus.NEW && message.getChar(ExecType.FIELD) == ExecType.NEW);
        } catch (FieldNotFound fieldNotFound) {
            fieldNotFound.printStackTrace();
        }
        return isAllowedParallelProcessing;
    }

    default boolean isIgnoreDropCopyMessage(Message message, SessionID sessionID) {
        return false;
    }
}
