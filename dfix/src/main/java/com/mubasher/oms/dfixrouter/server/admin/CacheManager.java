package com.mubasher.oms.dfixrouter.server.admin;

import com.mubasher.oms.dfixrouter.beans.DFIXMessage;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;

public class CacheManager implements Runnable {

    private static CacheManager instance;
    private static boolean isFirst;

    public static synchronized CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
            setIsFirst(true);
        }
        return instance;
    }

    private static void setIsFirst(boolean isFirst) {
        CacheManager.isFirst = isFirst;
    }

    @Override
    public void run() {
        if (isFirst) {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            setIsFirst(false);
        }
        FIXClient.getFIXClient().getApplication().clearCache();
        if (DFIXRouterManager.getFromExchangeQueue() != null) {
            DFIXRouterManager.getFromExchangeQueue().clearCache();
        }
        if (!Settings.getHostList().isEmpty()
                && (IConstants.SETTING_NO).equalsIgnoreCase(Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG))){
            sendCacheUpdateRequest();
        }
    }

    public void sendCacheUpdateRequest(){
        DFIXMessage dfixMsg = new DFIXMessage();
        dfixMsg.setMessage("DFIX INFORMATION REQUEST");
        dfixMsg.setType(Integer.toString(IConstants.DFIX_INFORMATION));
        DFIXRouterManager.getFromExchangeQueue().addMsg(dfixMsg);
    }
}
