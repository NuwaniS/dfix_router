package com.mubasher.oms.dfixrouter.util.stores;

import com.mubasher.oms.dfixrouter.beans.TradingMarketBean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TradingMarketStoreTest {

    @Test
    void saveMarketStatusPass(){
        String exchange = "TDWL";
        String marketSegId = "SAEQ";
        TradingMarketBean tradingMarketBean = new TradingMarketBean(exchange);
        tradingMarketBean.setMarketSegmentId(marketSegId);
        TradingMarketStore.saveMarketStatus(tradingMarketBean);
        Assertions.assertEquals(tradingMarketBean, TradingMarketStore.getTradingMarket(exchange, marketSegId));
    }
}
