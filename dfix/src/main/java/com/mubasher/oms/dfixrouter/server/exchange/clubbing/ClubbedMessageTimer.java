package com.mubasher.oms.dfixrouter.server.exchange.clubbing;

import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;

import java.util.Date;

/**
 * Created by nuwanis on 2/21/2018.
 */
public class ClubbedMessageTimer extends Thread {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.clubbing.ClubbedMessageTimer");

    private ExchangeExecutionMerger exchangeExecutionMerger;
    private long defaultTimeOut = 10000L;
    private long timeOut;

    public void init() {
        logger.info("Starting timeout thread");
        exchangeExecutionMerger = DFIXRouterManager.getInstance().getExchangeExecutionMerger();
        timeOut = Settings.getClubbingTimeOut() == 0L ? defaultTimeOut : Settings.getClubbingTimeOut();
    }

    @Override
    public void run() {
        while (DFIXRouterManager.getInstance().isStarted()) {
            try {
                DFIXRouterManager.sleepThread(timeOut);
                if (exchangeExecutionMerger.getClubbedMsgsMap().size() > 0) {
                    logger.info("Timeout happens at: " + new Date());
                    exchangeExecutionMerger.timeOut();
                }
            } catch (Exception e) {
                logger.error("Exception at ClubbedMessageTimer: " + e.getMessage(), e);
            }
        }
    }
}
