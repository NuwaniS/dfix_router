package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic;

import com.mubasher.oms.dfixrouter.beans.OrderBean;
import com.mubasher.oms.dfixrouter.beans.TradingMarketBean;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.ExchangeMessageProcessorI;
import com.mubasher.oms.dfixrouter.server.fix.FIXMessageProcessor;
import com.mubasher.oms.dfixrouter.util.stores.TradingMarketStore;
import quickfix.field.*;

public class KSEExchangeMessageProcessor implements ExchangeMessageProcessorI {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic.KSEExchangeMessageProcessor");
    private static final String EXCHANGE = "KSE";
    private static KSEExchangeMessageProcessor instance ;

    private KSEExchangeMessageProcessor(){}

    public static synchronized KSEExchangeMessageProcessor getInstance(){
        if (instance == null) {
            instance = new KSEExchangeMessageProcessor();
        }
        return instance;
    }

    @Override
    public boolean processMessage(String message) {
        boolean isSend = true;
        String msgType = FIXMessageProcessor.getFixTagValue(message, MsgType.FIELD);
        if (MsgType.TRADING_SESSION_STATUS.equals(msgType)) {
            saveTradingMarketStatus(message);
        } else if (MsgType.EXECUTION_REPORT.equals(msgType)) {
            isSend = processExecutionReport(message, isSend);
        }
        if (!isSend) {
            logger.info("Message Ignored: " + message);
        }
        return isSend;
    }

    private boolean processExecutionReport(String message, boolean isSend) {
        OrderBean order = new OrderBean();
        order.parseMessage(message);
        if (order.getExecutionType() == OrdStatus.REPLACED) {
            if (order.getClOrdId() != null  && order.getClOrdId().equals(order.getOrigClOrdId())) { //OrigClOrdId != null
                isSend = false;
            } else if (order.getTimeInForce() == TimeInForce.GOOD_TILL_CANCEL || order.getTimeInForce() == TimeInForce.GOOD_TILL_DATE){
                String marketSegmentId = order.getTradingSessionId();
                TradingMarketBean tradingMarketBean = TradingMarketStore.getTradingMarket(EXCHANGE, marketSegmentId);

                // Before ACCEPTANCE
                if(tradingMarketBean != null  //tradingMarketBean=>getTradingSessionId != null
                        && (FixConstants.FIX_VALUE_336_ENQUIRY.equals(tradingMarketBean.getTradingSessionId())
                            || FixConstants.FIX_VALUE_336_START_OF_DAY.equals(tradingMarketBean.getTradingSessionId())
                            || FixConstants.FIX_VALUE_336_ACCEPTANCE.equals(tradingMarketBean.getTradingSessionId())
                            || FixConstants.FIX_VALUE_336_PRE_OPEN.equals(tradingMarketBean.getTradingSessionId())
                            || FixConstants.FIX_VALUE_336_CLOSE.equals(tradingMarketBean.getTradingSessionId()))
                        && (order.getOrderStatus() == FixConstants.FIX_VALUE_39_ORDER_UNPLACED)){ // Unplaced messages ignore
                            isSend = false;
                }
            }
        }
        return isSend;
    }

    private void saveTradingMarketStatus(String message) {
        TradingMarketBean tradingMarketBean = new TradingMarketBean(EXCHANGE);
        tradingMarketBean.parseMessage(message);
        if (tradingMarketBean.getMarketSegmentId().equals("REG") && !tradingMarketBean.getTag1151().equals(FixConstants.FIX_VALUE_1151_AUCTION_MKT)) {
            if (tradingMarketBean.getTradingSessionId().equals(FixConstants.FIX_VALUE_336_ENQUIRY) && tradingMarketBean.getTag340().equals("100")) {
                logger.info( "Setting REG enquiry true");
                TradingMarketStore.setIsEnquiry(true);
            } else {
                logger.info( "Setting REG enquiry false");
                TradingMarketStore.setIsEnquiry(false);
            }
        }
        TradingMarketStore.saveMarketStatus(tradingMarketBean);
    }
}
