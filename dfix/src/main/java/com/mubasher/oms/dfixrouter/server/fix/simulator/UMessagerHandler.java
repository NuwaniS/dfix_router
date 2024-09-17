package com.mubasher.oms.dfixrouter.server.fix.simulator;

import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.SimulatorSettings;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.fix.FIXApplicationCommonLogic;
import com.mubasher.oms.dfixrouter.system.Settings;
import quickfix.*;
import quickfix.field.*;

import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class UMessagerHandler extends FIXApplicationCommonLogic {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.simulator.UMessagerHandler");
    private static final HashMap<SessionID, UMessagerHandler> uMessageHandlers = new HashMap<>();
    private static final SecureRandom random =  new SecureRandom();

    private UMessagerHandler() {
        super();
    }

    public static synchronized UMessagerHandler getUMessageHandler(SessionID sessionID) {
        UMessagerHandler uMessagerHandler;
        if (uMessageHandlers.keySet().contains(sessionID)) {
            uMessagerHandler = uMessageHandlers.get(sessionID);
        } else {
            uMessagerHandler = new UMessagerHandler();
            uMessageHandlers.put(sessionID, uMessagerHandler);
        }
        return uMessagerHandler;
    }

    public void addMsg(Message uMessage, SessionID sessionID) {
        try {
            sendToTarget(getResponseMessage(uMessage), sessionID);
        } catch (Exception e) {
            logger.error("Error Processing UMsgs: " + e.getMessage(), e);
        }
    }

    private Message getResponseMessage(Message message) throws FieldNotFound, ParseException {
        String messageType = message.getHeader().getString(MsgType.FIELD);
        Message uResponse = populateUResponse(message);
        SimpleDateFormat sdfyyyyMMdd = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat sdfyyyyMMddHHMmmss = new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
        int count = 5;
        Group group;
        switch (messageType) {
            case FixConstants.FIX_VALUE_35_CREATE_ACCOUNT_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_CREATE_ACCOUNT_RESPONSE);
                uResponse.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, new SimpleDateFormat("ddHHmmssSS").format(new Date(System.currentTimeMillis())));
                uResponse.setString(FixConstants.FIX_TAG_CREATE_CODE, "2");
                uResponse.setString(FixConstants.FIX_TAG_ACCOUNT_OWNER_NAME, "DFIXSimulator : ACCOUNT_OWNER_NAME");
                break;
            case FixConstants.FIX_VALUE_35_VIEW_ACCOUNT_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_VIEW_ACCOUNT_RESPONSE);
                uResponse.setString(FixConstants.FIX_TAG_ACCOUNT_OWNER_NAME, "DFIXSimulator : ACCOUNT_OWNER_NAME");
                uResponse.setInt(FixConstants.FIX_TAG_9710_COUNT, count);
                for (int i = 0; i < count; i++) {
                    group = new Group(FixConstants.FIX_TAG_9710_COUNT, FixConstants.FIX_TAG_ADDRESS);
                    group.setField(new StringField(FixConstants.FIX_TAG_ADDRESS, "DFIXSimulator : ADDRESS" + i));
                    uResponse.addGroup(group);
                }
                uResponse.setInt(FixConstants.FIX_TAG_100000_DUMMY_COUNT, count);
                for (int i = 0; i < count; i++) {
                    group = new Group(FixConstants.FIX_TAG_100000_DUMMY_COUNT, FixConstants.FIX_TAG_NAME);
                    group.setField(new StringField(FixConstants.FIX_TAG_NAME, "DFIXSimulator : NAME" + i));
                    uResponse.addGroup(group);
                }
                uResponse.setString(FixConstants.FIX_TAG_CITY, "DFIXSimulator : CITY");
                uResponse.setString(FixConstants.FIX_TAG_POSTAL_CODE, "DFIXSimulator : POSTAL_CODE");
                uResponse.setString(FixConstants.FIX_TAG_COUNTRY, "DFIXSimulator : COUNTRY");
                uResponse.setString(FixConstants.FIX_TAG_NIN, "DFIXSimulator : NIN");
                uResponse.setString(FixConstants.FIX_TAG_BANK_ACCOUNT_NUMBER, "DFIXSimulator : BANK_ACCOUNT_NUMBER");
                uResponse.setString(FixConstants.FIX_TAG_DELETE_RETURN_CODE, "0");
                uResponse.setString(FixConstants.FIX_TAG_GENDER, Long.parseLong(message.getString(FixConstants.FIX_TAG_ACCOUNT_NUMBER)) % 2 == 0 ? "M" : "F");
                uResponse.setString(FixConstants.FIX_TAG_MEMBER_CODE, message.getString(FixConstants.FIX_TAG_MEMBER_CODE));
                uResponse.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, message.getString(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
                break;
            case FixConstants.FIX_VALUE_35_DISABLE_ACCOUNT_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_DISABLE_ACCOUNT_RESPONSE);
                uResponse.setString(FixConstants.FIX_TAG_DELETE_RETURN_CODE, "0");
                uResponse.setString(FixConstants.FIX_TAG_MEMBER_CODE, message.getString(FixConstants.FIX_TAG_MEMBER_CODE));
                uResponse.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, message.getString(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
                break;
            case FixConstants.FIX_VALUE_35_PORTFOLIO_INQUIRY_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_PORTFOLIO_INQUIRY_RESPONSE);
                uResponse.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, message.getString(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
                uResponse.setString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE, "0");
                uResponse.setInt(NoRelatedSym.FIELD, count);
                addGroupsToResponseForPortfolioInqReq(count, sdfyyyyMMdd, uResponse);
                break;
            case FixConstants.FIX_VALUE_35_PLEDGE_UNPLEDGE_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_PLEDGE_UNPLEDGE_RESPONSE);
                uResponse.setString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE, "0");
                uResponse.setString(FixConstants.FIX_TAG_REFERENCE, message.getString(FixConstants.FIX_TAG_REFERENCE));
                uResponse.setString(FixConstants.FIX_TAG_SOURCE_TRANSACTION_NUMBER, getExecutionId());
                uResponse.setString(FixConstants.FIX_TAG_MEMBER_CODE, message.getString(FixConstants.FIX_TAG_MEMBER_CODE));
                uResponse.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, message.getString(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
                break;
            case FixConstants.FIX_VALUE_35_RIGHTS_SUBSCRIPTION_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_RIGHTS_SUBSCRIPTION_RESPONSE);
                uResponse.setString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE, "0");
                uResponse.setString(FixConstants.FIX_TAG_REFERENCE, message.getString(FixConstants.FIX_TAG_REFERENCE));
                uResponse.setString(FixConstants.FIX_TAG_SOURCE_TRANSACTION_NUMBER, getExecutionId());
                break;
            case FixConstants.FIX_VALUE_35_SUBSCRIPTION_COMMON_REQUEST:
            case FixConstants.FIX_VALUE_35_SUBSCRIPTION_NOMU_REQUEST:
            case FixConstants.FIX_VALUE_35_SUBSCRIPTION_BID_BOOK_REQUEST:
            case FixConstants.FIX_VALUE_35_SUBSCRIPTION_REIT_FUND_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_SUBSCRIPTION_RESPONSE);
                uResponse.setString(FixConstants.FIX_TAG_LM_REFERENCE_NB, message.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID));
                setOptionalTagFromRequest(message, uResponse, FixConstants.FIX_TAG_REFERENCE);
                uResponse.setString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE, "0");
                if (message.getInt(OrderQty.FIELD) == Settings.getInt(SimulatorSettings.REJECT_QTY)
                        || Long.parseLong(message.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID)) % Settings.getInt(SimulatorSettings.U_REJECT_PROPABILITY) == 0) {
                    populateUReject(uResponse, message.getHeader().getInt(MsgSeqNum.FIELD));
                } else {
                    uResponse.setString(FixConstants.FIX_TAG_ACCEPTED_DATE, sdfyyyyMMddHHMmmss.format(new Date()));
                    uResponse.setString(FixConstants.FIX_SUBSCRIPTION_STATUS, "1");
                    uResponse.setString(FixConstants.FIX_SUBMISSION_DATE, message.getString(FixConstants.FIX_SUBMISSION_DATE));
                }
                uResponse.setString(RefMsgType.FIELD, messageType);
                setOptionalTagFromRequest(message, uResponse, FixConstants.FIX_TAG_SYSTEM_ID);
                break;
            case FixConstants.FIX_VALUE_35_SUBSCRIPTION_CANCEL_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_SUBSCRIPTION_CANCEL_RESPONSE);
                uResponse.setString(FixConstants.FIX_TAG_LM_REFERENCE_NB, message.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID));
                setOptionalTagFromRequest(message, uResponse, FixConstants.FIX_TAG_REFERENCE);
                uResponse.setString(Symbol.FIELD, message.getString(Symbol.FIELD));
                uResponse.setString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE, "0");
                if (Long.parseLong(message.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID)) % Settings.getInt(SimulatorSettings.U_REJECT_PROPABILITY) == 0) {
                    populateUReject(uResponse, message.getHeader().getInt(MsgSeqNum.FIELD));
                }
                uResponse.setString(FixConstants.FIX_TAG_MEMBER_CODE, message.getString(FixConstants.FIX_TAG_MEMBER_CODE));
                uResponse.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, message.getString(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
                uResponse.setString(FixConstants.FIX_TAG_NIN, message.getString(FixConstants.FIX_TAG_NIN));
                setOptionalTagFromRequest(message, uResponse, FixConstants.FIX_TAG_SYSTEM_ID);
                uResponse.setString(FixConstants.FIX_SUBSCRIPTION_STATUS, "1");
                uResponse.setString(FixConstants.FIX_SUBMISSION_DATE, message.getString(FixConstants.FIX_SUBMISSION_DATE));
                break;
            case FixConstants.FIX_VALUE_35_RECONCILIATION_SUMMARY_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_RECONCILIATION_SUMMARY_RESPONSE);
                setOptionalTagFromRequest(message, uResponse, FixConstants.FIX_TAG_SYSTEM_ID);
                uResponse.setString(FixConstants.FIX_TAG_MEMBER_CODE, message.getString(FixConstants.FIX_TAG_MEMBER_CODE));
                uResponse.setString(Symbol.FIELD, message.getString(Symbol.FIELD));
                uResponse.setString(FixConstants.FIX_TAG_START_DATE, sdfyyyyMMdd.format(sdfyyyyMMddHHMmmss.parse(message.getString(FixConstants.FIX_SUBMISSION_DATE))));
                uResponse.setString(FixConstants.FIX_TAG_END_DATE, sdfyyyyMMdd.format(sdfyyyyMMddHHMmmss.parse(message.getString(FixConstants.FIX_SUBMISSION_DATE))));
                break;
            case FixConstants.FIX_VALUE_35_OFFER_COMPANY_LIST_REQUEST:
                uResponse.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_OFFER_COMPANY_LIST_RESPONSE);
                uResponse.setString(Symbol.FIELD, "DFIXSimulator : SYMBOL");
                uResponse.setString(FixConstants.FIX_TAG_STOCK_E_NAME, "DFIXSimulator : SYMBOL_E_NAME");
                uResponse.setString(FixConstants.FIX_TAG_STOCK_A_NAME, "DFIXSimulator : SYMBOL_A_NAME");
                uResponse.setString(FixConstants.FIX_TAG_SUBS_START_DATE, sdfyyyyMMddHHMmmss.format(new Date()));
                uResponse.setString(FixConstants.FIX_TAG_SUBS_END_DATE, sdfyyyyMMddHHMmmss.format(new Date()));
                uResponse.setDouble(FixConstants.FIX_TAG_ISSUE_PRICE, 100.00);
                uResponse.setInt(FixConstants.FIX_TAG_MIN_SUBSCRIPTION, 100);
                uResponse.setInt(FixConstants.FIX_TAG_MAX_SUBSCRIPTION, 150);
                uResponse.setString(FixConstants.FIX_TAG_ISSUER_COMPANY, "DFIXSimulator : ISSUER_COMPANY");
                uResponse.setString(FixConstants.FIX_TAG_LEAD_MANAGER, "DFIXSimulator : LEAD_MANAGER");
                uResponse.setInt(FixConstants.FIX_TAG_TOTAL_SHARES, 15000);
                uResponse.setInt(FixConstants.FIX_TAG_TOTAL_PUBLIC_SHARES_AMOUNT, 1500000);
                uResponse.setInt(FixConstants.FIX_TAG_MULTIPLIER, 1);
                uResponse.setInt(FixConstants.FIX_TAG_OFFER_TYPE, ((random.nextInt(10)) % 4) + 1);
                uResponse.setInt(FixConstants.FIX_TAG_FACE_VALUE, 1);
                uResponse.setInt(FixConstants.FIX_TAG_START_SEQUENCE, 1);
                uResponse.setInt(FixConstants.FIX_TAG_END_SEQUENCE, 100);
                group = new Group(FixConstants.FIX_TAG_BID_CNT, FixConstants.FIX_TAG_BID_SEQ);
                for (int i = 1; i <= count; i++) {
                    group.setInt(FixConstants.FIX_TAG_BID_SEQ, i);
                    group.setInt(FixConstants.FIX_TAG_BID_PRICE, i * 100);
                    uResponse.addGroup(group);
                }
                break;
            default:
                uResponse = null;
                logger.info("Simulator not implemented for: " + message.toString());
                break;
        }
        return uResponse;
    }

    private void addGroupsToResponseForPortfolioInqReq(int count, SimpleDateFormat sdfyyyyMMdd, Message uResponse) {
        Group group = new Group(NoRelatedSym.FIELD, Symbol.FIELD);
        for (int i = 0; i < count; i++) {
            group.setString(Symbol.FIELD, "DFIXSimulator : SYMBOL" + i);
            group.setString(FixConstants.FIX_TAG_ISIN, "DFIXSimulator : ISIN" + i);
            group.setDouble(Shares.FIELD, 100);
            group.setDouble(FixConstants.FIX_TAG_AVAILABLE_SHARES, 100);
            group.setDouble(FixConstants.FIX_TAG_PLEDGED_SHARES, 0);
            group.setString(FixConstants.FIX_TAG_POSITION_DATE, sdfyyyyMMdd.format(new Date()));
            group.setString(FixConstants.FIX_TAG_CHANGE_DATE, sdfyyyyMMdd.format(new Date()));
            uResponse.addGroup(group);
        }
    }

    private void setOptionalTagFromRequest(Message message, Message uResponse, int fixTag) throws FieldNotFound {
        if (message.isSetField(fixTag)) {
            uResponse.setString(fixTag, message.getString(fixTag));
        }
    }

    private Message populateUResponse(Message message) throws FieldNotFound {
        Message uResponse = new Message();
        uResponse.getHeader().setString(SenderCompID.FIELD, message.getHeader().getString(TargetCompID.FIELD));
        uResponse.getHeader().setString(TargetCompID.FIELD, message.getHeader().getString(SenderCompID.FIELD));
        if (message.getHeader().isSetField(SenderSubID.FIELD)) {
            uResponse.getHeader().setString(TargetSubID.FIELD, message.getHeader().getString(SenderSubID.FIELD));
        }
        if (message.getHeader().isSetField(TargetSubID.FIELD)) {
            uResponse.getHeader().setString(SenderSubID.FIELD, message.getHeader().getString(TargetSubID.FIELD));
        }
        uResponse.setString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID, message.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID));
        return uResponse;
    }

    private void populateUReject(Message uResponse, int refReqNo) {
        uResponse.setInt(RefSeqNum.FIELD, refReqNo);
        uResponse.setString(Text.FIELD, "DFIXSimulator : Rejected");
        int rejectionNumber = (refReqNo % 7) + 1;
        uResponse.setString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE, String.valueOf(rejectionNumber));
        switch (rejectionNumber){
            case 1:
                uResponse.setString(Text.FIELD, "Unknown ID");
                break;
            case 2:
                uResponse.setString(Text.FIELD, "Unknown Security");
                break;
            case 3:
                uResponse.setString(Text.FIELD, "Unknown Message Type");
                break;
            case 4:
                uResponse.setString(Text.FIELD, "Application not available");
                break;
            case 5:
                uResponse.setString(Text.FIELD, "Conditionally required field missing");
                break;
            case 6:
                uResponse.setString(Text.FIELD, "Not Authorized");
                break;
            case 7:
                uResponse.setString(Text.FIELD, "Subscription not exists");
                break;
            default:
                break;
        }
    }
}
