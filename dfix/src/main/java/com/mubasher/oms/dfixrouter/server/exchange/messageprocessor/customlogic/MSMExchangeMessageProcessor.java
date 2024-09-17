package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.ExchangeMessageProcessorI;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;
import quickfix.field.ResetSeqNumFlag;

public class MSMExchangeMessageProcessor implements ExchangeMessageProcessorI {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic.MSMExchangeMessageProcessor");

    private static MSMExchangeMessageProcessor instance ;

    private MSMExchangeMessageProcessor(){}

    public static synchronized MSMExchangeMessageProcessor getInstance(){
        if (instance == null) {
            instance = new MSMExchangeMessageProcessor();
            }
        return instance;
    }

    //regenerate logon message with 141=Y
    @Override
    public void regenLogOnMsg(Message message, SessionID sessionID, String exchange) {
        Message.Header header = message.getHeader();
        try {
            if (header.getField(new MsgType()).valueEquals(MsgType.LOGON) && message.isSetField(new ResetSeqNumFlag()) && message.getField(new ResetSeqNumFlag()).valueEquals(true)) {
                DFIXMessage dfixMsg = new DFIXMessage();
                Message reLogOn = (Message) message.clone();
                FIXClient.getFIXClient().getApplication().addLogonField(reLogOn, sessionID);
                dfixMsg.setExchange(exchange);
                dfixMsg.setMessage(reLogOn.toString());
                DFIXRouterManager.getToExchangeQueue().addMsg(dfixMsg.composeMessage());
            }
        } catch (quickfix.FieldNotFound ee) {
            // Keep silent as this is not an error
        } catch (Exception e) {
            logger.error("Error at MSM regenLogOnMsg: " + e.getMessage(), e);
        }
    }
}
