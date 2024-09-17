package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.customlogic;

import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.ExchangeMessageProcessorI;

public class DefaultExchangeMessageProcessor implements ExchangeMessageProcessorI {
    private static DefaultExchangeMessageProcessor instance;

    private DefaultExchangeMessageProcessor() {
    }

    public static synchronized DefaultExchangeMessageProcessor getInstance() {
        if (instance == null) {
            instance = new DefaultExchangeMessageProcessor();
        }
        return instance;
    }
}
