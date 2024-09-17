package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic;

import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.ExchangeMessageProcessorI;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import quickfix.FieldNotFound;
import quickfix.Group;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.*;

public class TDWLExchangeMessageProcessor implements ExchangeMessageProcessorI {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic.TDWLExchangeMessageProcessor");
    private static TDWLExchangeMessageProcessor instance ;

    private TDWLExchangeMessageProcessor(){}

    public static synchronized TDWLExchangeMessageProcessor getInstance(){
        if (instance == null ) {
            instance = new TDWLExchangeMessageProcessor();
        }
        return instance;
    }

    @Override
    public boolean isAllowedParallelProcessing(Message message){
        boolean isAllowedParallelProcessing;
        try {
            isAllowedParallelProcessing = (message.getChar(OrdStatus.FIELD) == OrdStatus.NEW && message.getChar(ExecType.FIELD) == ExecType.NEW);
            if (isAllowedParallelProcessing){
                if (message.isSetField(FixConstants.FIX_TAG_EXCHANGE_ORDER_ID)){
                    isAllowedParallelProcessing = false;
                } else if (message.isSetField(ClOrdID.FIELD)){
                    Integer.parseInt(message.getString(ClOrdID.FIELD)); //not to allow drop copy orders to p.process
                }
            }
        } catch (Exception e){
            isAllowedParallelProcessing = false;
            logger.error("Error at TDWL isAllowedParallelProcessing: " + e.getMessage(), e);
        }
        return isAllowedParallelProcessing;
    }

    @Override
    public boolean isIgnoreDropCopyMessage(Message message, SessionID sessionID) {
        boolean isIgnore = false;
        try {
            String ignDropCopy = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true).getProperty(IConstants.SETTING_IGNORE_DROP_COPY);
            if (ignDropCopy != null && ignDropCopy.length() > 0 && message.getHeader().getString(MsgType.FIELD).equals(MsgType.EXECUTION_REPORT)
                    && message.isSetField(ClOrdID.FIELD)) {
                String[] ignDropCopyArr = splitIntoTwoString(ignDropCopy);
                if (message.getString(ClOrdID.FIELD).startsWith(ignDropCopyArr[0])) {
                    if (getExchangeAccount(message).equals(ignDropCopyArr[1])) {
                        isIgnore = true;
                    }
                    logger.debug("Drop copy(" + message.getString(ClOrdID.FIELD) + ") to be ignored from TDWL session:" + isIgnore);
                }
            }
        } catch (Exception e) {
            logger.error("Exception Occurred in isIgnoreDropCopyMessage:"+ e.getMessage());
            isIgnore = false;
        }
        return isIgnore;
    }

    public String getExchangeAccount(Message message) throws FieldNotFound {
        String account = "";
        if (message.isSetField(Account.FIELD)) {
            account = message.getString(Account.FIELD);
            if (message.hasGroup(NoPartyIDs.FIELD)) {
                for (Group group : message.getGroups(NoPartyIDs.FIELD)) {
                    if (group.isSetField(PartyRole.FIELD) && group.getInt(PartyRole.FIELD) == PartyRole.CLIENT_ID) {
                        account = group.getString(PartyID.FIELD);
                    }
                }
            }
        }
        return account;
    }

    public static String[] splitIntoTwoString(String input) {
        int index = input.indexOf(IConstants.COMMA_PATTERN);
        if (index == IConstants.CONSTANT_MINUS_1) {
            return new String[]{input};
        }
        String firstPart = input.substring(IConstants.CONSTANT_ZERO_0, index);
        String secondPart = input.substring(index + IConstants.CONSTANT_ONE_1);
        return new String[]{firstPart, secondPart};
    }
}
