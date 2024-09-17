package com.mubasher.oms.dfixrouter.beans;

import java.util.StringTokenizer;

public class TradingMarketBean extends FIXMessageBean {
    private String exchange = null;
    private String marketSegmentId = null;
    private String tag340 = "";
    private String tag1151 = "";

    public TradingMarketBean(String exchange) {
        this.exchange = exchange;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getMarketSegmentId() {
        return marketSegmentId;
    }

    public void setMarketSegmentId(String marketSegmentId) {
        this.marketSegmentId = marketSegmentId;
    }

    public String getTag340() {
        return tag340;
    }

    public void setTag340(String tag340) {
        this.tag340 = tag340;
    }

    public String getTag1151() {
        return tag1151;
    }

    public void setTag1151(String tag1151) {
        this.tag1151 = tag1151;
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
                case 1300:
                    marketSegmentId = st1.nextToken();
                    break;
                case 340:
                    tag340 = st1.nextToken();
                    break;
                case 1151:
                    tag1151 = st1.nextToken();
                    break;
                default:
                    break;
            }
        }
    }
}
