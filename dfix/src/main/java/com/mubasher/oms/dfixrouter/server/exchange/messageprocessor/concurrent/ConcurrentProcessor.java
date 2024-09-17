package com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.concurrent;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.beans.InternalBean;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.ExchangeMessageProcessorFactory;
import com.mubasher.oms.dfixrouter.server.fix.FIXApplicationCommonLogic;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.stores.TradingMarketStore;
import quickfix.*;

import java.util.Map;
import java.util.Properties;

/**
 * Created by randulal on 11/30/2018.
 */
public class ConcurrentProcessor implements Runnable {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.exchange.messageprocessor.concurrent.ConcurrentProcessor");
    MessageQHandler handler = null;
    private static int processCount = 0;
    private static boolean isStartupDelay = true;
    private static long startupDelay = 4000;
    private static long processingInterval = 2000;
    private static long waitingInterval = 4000;
    private static int splitSize = 10;

    public static void setProcessCount(int pCount){
        processCount = pCount;
    }

    static int getProcessCount(){
        return processCount ;
    }

    public static void setIsStartupDelay(boolean isDelay){
        isStartupDelay = isDelay;
    }

    public static boolean getIsStartupDelay() {
        return isStartupDelay;
    }

    public ConcurrentProcessor(MessageQHandler handler) {
        this.handler = handler;
        initStaticFields();
    }

    private static void initStaticFields() {
        startupDelay = Long.parseLong(Settings.getProperty(SettingsConstants.UNPLACED_PROCESS_START_DELAY));
        processingInterval = Long.parseLong(Settings.getProperty(SettingsConstants.UNPLACED_PROCESSING_INTERVAL));
        waitingInterval = Long.parseLong(Settings.getProperty(SettingsConstants.UNPLACED_PROCESS_START_WAITING_INTERVAL));
        splitSize = Integer.parseInt(Settings.getProperty(SettingsConstants.UNPLACED_PROCESSING_SPLIT_SIZE));
    }

    @Override
    public void run() {
        // close this thread when market open todo !!!!!!!!!! check list should start in enquiry , stop in mkt Open if Q size 0
        while (true) {
            if (!TradingMarketStore.isEnquiry()) {
                handleNonEnquiryState();
            } else {
                handleEnquiryState();
            }
        }
    }

    private void handleEnquiryState() {
        setIsStartupDelay(true);
        try {
            Thread.sleep(waitingInterval);
        } catch (InterruptedException e) {
            logger.info("3 InterruptedException in ConcurrentProcessor");
        }
    }

    private void handleNonEnquiryState() {
        if (getIsStartupDelay()) {
            processStartupDelay();
        } else {
            processMessages();
        }
    }

    private void processMessages() {
        while (!handler.getMessageQueue().isEmpty()) {
            try {
                InternalBean internalBean = handler.getMessageQueue().take();
                processSendInternalBean(internalBean);
                if (processCount == splitSize) {
                    setProcessCount(0);
                    break;
                }
                setProcessCount(processCount+1);
            } catch (InterruptedException e) {
                logger.info("Thread interrupted ");
            }
        }
        try {
            Thread.sleep(processingInterval);
        } catch (InterruptedException e) {
            logger.info("2 InterruptedException in ConcurrentProcessor");
        }
    }

    private void processSendInternalBean(InternalBean internalBean) {
        boolean isSend;
        Message message = internalBean.getMessage();
        SessionID sessionID = internalBean.getSessionId();
        isSend = ExchangeMessageProcessorFactory.processMessage(getSessionIdentifier(sessionID), message.toString());
        if (isSend) {
            logger.info("sending from Thread " + Thread.currentThread().getName());
            processTagModifications(message, sessionID, true);

            DFIXMessage dfixMsg = new DFIXMessage();
            dfixMsg.setExchange(getSessionIdentifier(sessionID));
            dfixMsg.setSequence(FIXApplicationCommonLogic.getExecutionId());
            dfixMsg.setType(Integer.toString(IConstants.APPLICATION_MESSAGE_RECEIVED));
            dfixMsg.setMessage(message.toString());
            DFIXRouterManager.getFromExchangeQueue().addMsg(dfixMsg);
        }
    }

    private void processStartupDelay() {
        setIsStartupDelay(false);
        try {
            Thread.sleep(startupDelay);
        } catch (InterruptedException e) {
            logger.info("1 InterruptedException in ConcurrentProcessor");
        }
    }

    private void processTagModifications(Message message, SessionID sessionID, boolean isRemoveTag) {
        try {
            int modifyTag;
            String modifyTagValue;
            Properties prop = FIXClient.getSettings().get(sessionID).getSessionProperties(sessionID, true);
            if (prop.containsKey(IConstants.SETTING_TAG_MODIFICATION)
                    && prop.get(IConstants.SETTING_TAG_MODIFICATION).toString().equalsIgnoreCase(IConstants.SETTING_YES)) {
                for (Map.Entry<Object, Object> entry : prop.entrySet()) {
                    String key = entry.getKey().toString();
                    if (key.startsWith(IConstants.SETTING_TAG_MODIFICATION_TAG)
                            && !key.equalsIgnoreCase(IConstants.SETTING_TAG_MODIFICATION)) {
                        modifyTagValue = prop.get(key).toString();     //FIX
                        modifyTag = Integer.parseInt(key.substring(3));   //remove 'Tag'
                        applyTagModifications(message, isRemoveTag, modifyTag, modifyTagValue);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error at processTagModifications: " + e.getMessage());
        }
    }

    private void applyTagModifications(Message message, boolean isRemoveTag, int modifyTag, String modifyTagValue) throws FieldNotFound {
        if (message.isSetField(modifyTag)) {
            if (isRemoveTag){
                modifyTagValue = message.getString(modifyTag).replace(modifyTagValue, "");
            } else{
                modifyTagValue = modifyTagValue + message.getString(modifyTag);
            }
            message.removeField(modifyTag);
            message.setString(modifyTag, modifyTagValue);
        }
    }

    private String getSessionIdentifier(SessionID sessionID) {
        return FIXClient.getFIXClient().getApplication().getSessionIdentifier(sessionID);
    }

}
