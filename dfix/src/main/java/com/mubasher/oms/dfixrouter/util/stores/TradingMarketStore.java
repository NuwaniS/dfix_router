package com.mubasher.oms.dfixrouter.util.stores;

import com.mubasher.oms.dfixrouter.beans.TradingMarketBean;

import java.util.HashMap;

public class TradingMarketStore {
    private static HashMap<String, TradingMarketBean> tradingMarkets = new HashMap<>();
    private static boolean isEnquiry = false;

    public static boolean isEnquiry() {
        return isEnquiry;
    }

    public static void setIsEnquiry(boolean isEnquiry) {
        TradingMarketStore.isEnquiry = isEnquiry;
    }

    private TradingMarketStore() {
        super();
    }

    public static void saveMarketStatus(TradingMarketBean tradingMarketBean) {
        String key = tradingMarketBean.getExchange().toUpperCase() + "_" + tradingMarketBean.getMarketSegmentId().toUpperCase();
        tradingMarkets.put(key, tradingMarketBean);
    }

    public static TradingMarketBean getTradingMarket(String exchange, String marketSegmentId) {
        String key = exchange.toUpperCase() + "_" + marketSegmentId.toUpperCase();
        return tradingMarkets.get(key);
    }
}
