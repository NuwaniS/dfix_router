package com.mubasher.oms.dfixrouter.server.failover;

import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.cluster.DFIXCluster;
import com.mubasher.oms.dfixrouter.system.Settings;

/**
 * Created by nuwanis on 10/17/2017.
 */
public class FailOverHandler extends Thread {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.failover.FailOverHandler");
    private int failoverAttempts;
    private int failoverInterval;
    private DFIXRouterManager dfixRouterManager = DFIXRouterManager.getInstance();
    private DFIXCluster dfixCluster = DFIXCluster.getInstance();

    public FailOverHandler() {
        logger.info("FailOverHandler started");
        failoverAttempts = Settings.getInt(SettingsConstants.FAILOVER_ATTEMPTS);
        failoverInterval = Settings.getInt(SettingsConstants.FAILOVER_INTERVAL);
    }

    @Override
    public void run() {
        for (int i = 0; i < failoverAttempts; i++) {
            logger.info("Check Primary node status in Cluster: Attempt - " + i);
            dfixCluster.askWhoIsPrimary();
            DFIXRouterManager.sleepThread(failoverInterval);
            if (!dfixCluster.isAllowedToActivate()) {   //There is an active node in the cluster
                logger.info("Active DFIX joined the cluster : " + dfixCluster.getPrimaryNode());
                logger.info("FailOver Process Stopped");
                break;
            }
        }
        try {
            dfixRouterManager.startDFIXRouterManager();
        } catch (Exception e) {
            logger.error("Exception at FailOver: " + e.getMessage(), e);
            DFIXRouterManager.exitApplication(0);
        }
    }

    public int getFailoverAttempts() {
        return failoverAttempts;
    }

    public void setFailoverAttempts(int failoverAttempts) {
        this.failoverAttempts = failoverAttempts;
    }

    public int getFailoverInterval() {
        return failoverInterval;
    }

    public void setFailoverInterval(int failoverInterval) {
        this.failoverInterval = failoverInterval;
    }
}
