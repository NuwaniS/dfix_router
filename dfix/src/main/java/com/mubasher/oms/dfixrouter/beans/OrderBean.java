package com.mubasher.oms.dfixrouter.beans;

import com.mubasher.oms.dfixrouter.constants.FixConstants;
import quickfix.Message;
import quickfix.SessionID;

import java.util.StringTokenizer;


public class OrderBean extends FIXMessageBean {

    private String clOrdId = null;
    private String origClOrdId = null;
    private char executionType = FixConstants.FIX_VALUE_150_DEFAULT;
    private char timeInForce;
    private char orderStatus = FixConstants.FIX_VALUE_39_DEFAULT;
    private Message message;
    private SessionID sessionID;

    public String getClOrdId() {
        return clOrdId;
    }

    public void setClOrdId(String clOrdId) {
        this.clOrdId = clOrdId;
    }

    public String getOrigClOrdId() {
        return origClOrdId;
    }

    public void setOrigClOrdId(String origClOrdId) {
        this.origClOrdId = origClOrdId;
    }

    public char getExecutionType() {
        return executionType;
    }

    public void setExecutionType(char executionType) {
        this.executionType = executionType;
    }

    public char getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(char timeInForce) {
        this.timeInForce = timeInForce;
    }

    public char getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(char orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public SessionID getSessionID() {
        return sessionID;
    }

    public void setSessionID(SessionID sessionID) {
        this.sessionID = sessionID;
    }

    public void parseMessage(String sFixMessage) {
        String sToken;
        String sTag = null;
        int iTag;
        StringTokenizer st = new StringTokenizer(sFixMessage, "\u0001");
        StringTokenizer st1;

        while (st.hasMoreTokens()) {
            sToken = st.nextToken();
            st1 = new StringTokenizer(sToken, "=");
            if (st1.countTokens() == 2){
                sTag = st1.nextToken();
            }
            try {
                iTag = Integer.parseInt(sTag);
            } catch (NumberFormatException e) {
                continue;
            }
            switch (iTag) {
                case 35:
                    fixMsgType = st1.nextToken();
                    break;
                case 336:
                    tradingSessionId = st1.nextToken();
                    break;
                case 11:
                    clOrdId = st1.nextToken();
                    break;
                case 41:
                    origClOrdId = st1.nextToken();
                    break;
                case 39:
                    orderStatus = st1.nextToken().charAt(0);
                    break;
                case 59:
                    timeInForce = st1.nextToken().charAt(0);
                    break;
                case 150:
                    executionType = st1.nextToken().charAt(0);
                    break;
                default:
                    break;
            }
        }
    }
}
