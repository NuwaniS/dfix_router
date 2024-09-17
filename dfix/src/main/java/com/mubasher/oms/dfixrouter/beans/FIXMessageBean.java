package com.mubasher.oms.dfixrouter.beans;

public class FIXMessageBean {
    protected String fixMsgType;
    protected String tradingSessionId = null;

    public String getFixMsgType() {
        return fixMsgType;
    }

    public void setFixMsgType(String fixMsgType) {
        this.fixMsgType = fixMsgType;
    }

    public String getTradingSessionId() {
        return tradingSessionId;
    }

    public void setTradingSessionId(String tradingSessionId) {
        this.tradingSessionId = tradingSessionId;
    }
}
