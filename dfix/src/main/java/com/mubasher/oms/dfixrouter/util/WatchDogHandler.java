package com.mubasher.oms.dfixrouter.util;

import com.dfn.watchdog.agent.WatchdogAgent;
import com.dfn.watchdog.agent.listeners.AgentCallbackListener;
import com.dfn.watchdog.commons.exceptions.InvalidConfigurationError;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.fix.FIXMessageProcessor;
import com.mubasher.oms.dfixrouter.system.Settings;
import quickfix.Message;

import java.util.concurrent.Executors;

/**
 * Created by nuwanis on 8/31/2017.
 */
public class WatchDogHandler {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.util.WatchDogHandler");

    private static AgentCallbackListenerDfix agentCallbackListener = null;

    public static void runWatchdogAgent() {
        logger.debug("Starting watchdog agent.......");
        try {
            String falconIp = Settings.getProperty(SettingsConstants.FALCON_IP);
            int falconPort = Settings.getProperty(SettingsConstants.FALCON_PORT) == null ? 0:
                    Integer.parseInt(Settings.getProperty(SettingsConstants.FALCON_PORT));
            String falconIpSecondary = Settings.getProperty(SettingsConstants.FALCON_IP_SECONDARY);
            int falconPortSecondary = Settings.getProperty(SettingsConstants.FALCON_PORT_SECONDARY) == null ? 0:
                    Integer.parseInt(Settings.getProperty(SettingsConstants.FALCON_PORT_SECONDARY));

            String authNodeCategoryName = Settings.getProperty(SettingsConstants.FALCON_AUTHENTICATION_NODE_CATEGORY);
            String authNodePassword = Settings.getProperty(SettingsConstants.FALCON_AUTHENTICATION_NODE_PASSWORD);
            boolean sslAgentEnabled = IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.FALCON_SSL_AGENT_ENABLED));

            agentCallbackListener = new AgentCallbackListenerDfix();
            WatchdogAgent watchdogAgent = getWatchdogAgent()
                    .configure(agentCallbackListener, Executors.newCachedThreadPool())
                    .enableWatchdog(true)
                    .setNode((short) Integer.parseInt(Settings.getProperty(IConstants.SETTING_DFIX_ID)), SettingsConstants.CLUSTER_MEMBER_PREFIX)
                    .setServer(falconIp, falconPort)
                    .setSecondaryServer(falconIpSecondary, falconPortSecondary);

            if (authNodeCategoryName != null && authNodePassword != null) {
                watchdogAgent
                        .setFalconAuthData(authNodeCategoryName, authNodePassword);
            }
            if (sslAgentEnabled) {
                String keystorePath = Settings.getProperty(SettingsConstants.FALCON_SSL_CLIENT_KEYSTORE);
                String keystorePassword = Settings.getProperty(SettingsConstants.FALCON_SSL_CLIENT_ENC_KEYSTORE_PASSWORD);
                String truststorePath = Settings.getProperty(SettingsConstants.FALCON_SSL_CLIENT_TRUSTSTORE);
                String truststorePassword = Settings.getProperty(SettingsConstants.FALCON_SSL_CLIENT_ENC_TRUSTSTORE_PASSWORD);
                watchdogAgent.setTLSConfig(true, keystorePath, keystorePassword, truststorePath, truststorePassword);
            }
            watchdogAgent
                    .build()
                    .run();
        } catch (Exception e) {
            logger.error("Watchdog agent initialization error : " + e.getMessage());
            throw e;
        }
    }

    static WatchdogAgent getWatchdogAgent() {
        return WatchdogAgent.INSTANCE;
    }

    public static void sendLinkStatus(String hostId, String dfixId, com.dfn.watchdog.commons.State state) {
        getWatchdogAgent().getListener().sendLinkStatus(hostId, dfixId, state);
    }

    public static void sendExchangeLinkStatus(String dfixId, String sesId, com.dfn.watchdog.commons.State state, String sessionIdentifier) {
        ((AgentCallbackListenerDfix) getWatchdogAgent().getListener()).sendExchangeLinkStatus(dfixId, sesId, state, sessionIdentifier);
    }

    public static int getAppServerId(Message message) {
        int routeId = -1;   //In accordance with the servedById in DFIX
        try {
            if (message.isSetField(FixConstants.FIX_TAG_10008) && message.getInt(FixConstants.FIX_TAG_10008) > 0) {
                String tenantCode = FIXMessageProcessor.getTenantCode(message);
                if (IConstants.DEFAULT_TENANT_CODE.equals(tenantCode)) {
                    routeId = agentCallbackListener.next(message.getInt(FixConstants.FIX_TAG_10008));   //Get OMS id from watchog agent
                } else {
                    routeId = agentCallbackListener.next(message.getInt(FixConstants.FIX_TAG_10008), tenantCode);
                }
                routeId = Settings.getHostIdForOmsId(routeId);     //Get host id for the returned oms id, -1 is returned when the mapping is not found
            }   //else routeId = -1 and msg is broadcasted to all OMS
        } catch (Exception e) {
            logger.error("Route id request failed from agent: " + e.getMessage());
        }
        return routeId;
    }

    public static int getAppServerId(String message) {
        int routeId = -1;   //In accordance with the servedById in DFIX
        try {
            String customerIdString = FIXMessageProcessor.getFixTagValue(message, FixConstants.FIX_TAG_10008);
            if (customerIdString != null) {
                int customerId = Integer.parseInt(customerIdString);
                String tenantCode = FIXMessageProcessor.getTenantCode(message);
                if (IConstants.DEFAULT_TENANT_CODE.equals(tenantCode)) {
                    routeId = agentCallbackListener.next(customerId);   //Get OMS id from watchog agent
                } else {
                    routeId = agentCallbackListener.next(customerId, tenantCode);
                }
                routeId = Settings.getHostIdForOmsId(routeId);     //Get host id for the returned oms id, -1 is returned when the mapping is not found
            }
        } catch (Exception e) {
            logger.error("Route id request failed from agent: " + e.getMessage());
        }
        return routeId;
    }

    public static void waitForRegistration() {
        if ((IConstants.SETTING_YES).equalsIgnoreCase(Settings.getProperty(SettingsConstants.ENABLE_FALCON_REGISTRATION))) {
            logger.info("waiting for falcon registration completes.");
            getWatchdogAgent().getAgentInitializer().getAgentRegistrationHandler().waitForRegistration(5);
            if (!getWatchdogAgent().isRegistrationDone()) {
                logger.error("Registration is not completed with falcon! Please check Falcon Connectivity!");
                throw new InvalidConfigurationError("Falcon Registration Error!!");
            }
        }
    }

    public AgentCallbackListener getAgentCallbackListener() {
        return agentCallbackListener;
    }
}
