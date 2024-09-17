package com.mubasher.oms.dfixrouter.util;

import com.dfn.watchdog.agent.WatchdogAgent;
import com.dfn.watchdog.agent.listeners.AgentCallbackListenerSimple;
import com.dfn.watchdog.commons.*;
import com.dfn.watchdog.commons.messages.cluster.ClusterUpdate;
import com.dfn.watchdog.commons.messages.cluster.RegistrationAck;
import com.dfn.watchdog.commons.messages.monitoring.ExternalLinkStatus;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;

import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AgentCallbackListenerDfix extends AgentCallbackListenerSimple {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.util.AgentCallbackListenerDfix");

    public void sendExchangeLinkStatus(String dfixId, String sesId, com.dfn.watchdog.commons.State state, String sessionIdentifier) {
        ExternalLinkStatus externalLinkStatus = new ExternalLinkStatus(dfixId, sesId, state, sessionIdentifier);
        notifyServer(externalLinkStatus);
    }

    //This method is triggered when the view of the cluster is changed.
    @Override
    public void updateConfiguration(View view) {
        if (view == null || WatchdogAgent.INSTANCE.getView().getNodeMap().size() == 0) {
            return;
        }
        if (!DFIXRouterManager.getInstance().isStarted()) {
            return;
        }

        try {
            for (Node node : view.getAllNodes(NodeType.OMS).values()) {
                if (node.getState() == State.CLOSED || node.getState() == State.CONNECTED) {    //If the node is in an end state
                    int hostId = Settings.getHostIdForOmsId(node.getId());     //Get host id for the oms id
                    if (hostId == -1) {     //If OMS id is not configured in dfix
                        continue;
                    }
                    DFIXRouterManager.getFromExchangeQueue().resendStoredMessages(hostId);
                    //Invoke the same method after 50 seconds
                    //This is to resend the messages picked up by the QueueExceptionListener
                    Executors.newScheduledThreadPool(1).schedule(() -> DFIXRouterManager.getFromExchangeQueue().resendStoredMessages(hostId), 120, TimeUnit.SECONDS);
                    logger.info("OMS Node change detected: OMS_ID" + node.getId());
                }
            }
        } catch (Exception e) {
            logger.error("Error on updateConfiguration for view:  " + view + ": " + e.getMessage(), e);
        }
    }

    /*This method is called when DFIX joins the cluster, this is the first method call when it joins the cluster
     * and when the falcon server swaps from secondary to primary
     * This method is called componentRegistrationEnabled : true (falcon config) scenario only
     * This method is not invoked when other components join the cluster*/
    @Override
    public void updateConfigurationRegistration(RegistrationAck registrationAck) {
        logger.info("update cluster configuration message received. ");
        if (!IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.ENABLE_FALCON_REGISTRATION))) {
            logger.error("Update Cluster Registration is not supported when ENABLE_FALCON_REGISTRATION = N");
            return;
        }
        if (DFIXRouterManager.getInstance().isStarted()) {
            logger.error("DFIX is already registered. Skip Registration Message");
            return;
        }
        ClusterConfiguration clusterConfiguration = registrationAck.getClusterConfiguration();
        // node id
        Settings.setProperty(IConstants.SETTING_DFIX_ID, String.valueOf(registrationAck.getNodeId()));
        Settings.getHostList().clear();
        Settings.getHostIdMap().clear();
        for (ClusterServerConfig clusterServerConfig : clusterConfiguration.getOmsList()) {
            Host host = addHostToSettings(clusterServerConfig);
            logger.info("New Host added at Registration" + host);
        }
    }

    /*This method is called when the state of a node changes in the cluster
     * For DFIX OMS Add event adds a host, OMS remove event removes a host*/
    @Override
    public void updateClusterConfiguration(ClusterUpdate clusterUpdate) {
        logger.info("ClusterUpdate message received.");
        if (!IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.ENABLE_FALCON_REGISTRATION))) {
            logger.error("Update Cluster Configuration is not supported when ENABLE_FALCON_REGISTRATION = N");
            return;
        }
        ClusterServerConfig clusterServerConfig = clusterUpdate.getUpdatedServer();
        boolean isAdded = clusterUpdate.isAdded();
        if (isAdded) {
            addToHostList(clusterServerConfig);
        } else {
            removeFromHostList(clusterServerConfig);
        }
    }

    private void removeFromHostList(ClusterServerConfig clusterServerConfig) {
        if (clusterServerConfig.getNodeType() == NodeType.OMS) {
            Host hostToRemove = null;
            for (Host dfixHost : Settings.getHostList()) {
                if (dfixHost.getOmsId() == clusterServerConfig.getNodeId()) {
                    hostToRemove = dfixHost;
                    break;
                }
            }
            if (hostToRemove != null) {
                DFIXRouterManager.getInstance().stopToExchangeQueue(hostToRemove.getId());
                DFIXRouterManager.getInstance().stopFromExchangeQueue(hostToRemove.getOmsId());
                removeHostFromSettings(hostToRemove);
                logger.info("Host removed. oms id: " + clusterServerConfig.getNodeId());
            }
        }
    }

    private void addToHostList(ClusterServerConfig clusterServerConfig) {
        if (clusterServerConfig.getNodeType() == NodeType.OMS) {
            if (!isEligibleToAddHost(clusterServerConfig)) {
                return;
            }
            try {
                Host host = addHostToSettings(clusterServerConfig);
                DFIXRouterManager.getInstance().startToExchangeQueue(host);
                DFIXRouterManager.getInstance().startFromExchangeQueue(host);
                logger.info("New Host added at OMS Add" + host);
            } catch (Exception e) {
                logger.error("Error on initializing oms Host", e);
            }
        }
    }

    private synchronized Host addHostToSettings(ClusterServerConfig clusterServerConfig) {
        Host host = createHostFromServer(clusterServerConfig, Settings.getHostList().size() + 1);
        Settings.getHostList().add(host);
        Settings.getHostIdMap().put(host.getOmsId(), host.getId());
        return host;
    }

    private synchronized void removeHostFromSettings(Host hostToRemove) {
        Settings.getHostList().remove(hostToRemove);
        Settings.getHostIdMap().remove(hostToRemove.getOmsId());
        Settings.getHostIdMap().replaceAll((key, val) -> getUpdatedHostId(val, hostToRemove.getId()));
        Settings.getHostList().replaceAll(host -> getUpdatedHostWithNewId(host, hostToRemove.getId()));
    }

    private Host getUpdatedHostWithNewId(Host host, int removedHostId) {
        host.setId(getUpdatedHostId(host.getId(), removedHostId));
        return host;
    }

    /*This method is to adjust the host ids when a host is removed from the cluster.
    Because their host id and the position in the array list should be matched*/
    private int getUpdatedHostId(int hostId, int removedHostId) {
        if (hostId > removedHostId) {
            return hostId - 1;
        } else {
            return hostId;
        }
    }

    private Host createHostFromServer(ClusterServerConfig clusterServerConfig, int index) {
        Host host = new Host();
        host.setId(index);
        host.setOmsId(clusterServerConfig.getNodeId());
        host.setIp(clusterServerConfig.getIp());
        host.setPort(clusterServerConfig.getPort());
        host.setReqQName(Settings.getProperty("HOST_FROM_QUEUE"));
        host.setResQName(Settings.getProperty("HOST_TO_QUEUE"));
        host.setClubQName(Settings.getProperty("HOST_CLUBBED_QUEUE"));
        host.setMiddleware(Settings.getProperty("HOST_MIDDLEWARE"));
        host.setContextFactory(Settings.getProperty("HOST_INITIAL_CONTEXT_FACTORY"));
        host.setProviderURL(Settings.getProperty("HOST_EJB_CLIENT_PROVIDER_URL") + host.getIp() + ":" + host.getPort());
        host.setConnectionFactory(Settings.getProperty("HOST_CONNECTION_FACTORY"));
        host.setUserName(Settings.getProperty("HOST_USERNAME"));
        host.setPassword(Settings.getProperty("HOST_PASSWORD"));
        host.setIntermediateQueue(Settings.getProperty("HOST_INTERMEDIATE_QUEUE"));
        host.setuMessageQueue(Settings.getProperty("HOST_U_MESSAGE_QUEUE"));
        host.setChannel(Settings.getProperty("HOST_CHANNEL_NAME"));
        host.setUrlPkgPrefixes(Settings.getProperty("HOST_URL_PKG_PREFIXES"));
        host.setUrlPkgPrefixesValue(Settings.getProperty("HOST_URL_PKG_PREFIXES_VALUE"));
        host.setSSLCipherSuite(Settings.getProperty("HOST_SSL_CIPHER_SUITE"));
        host.setMqQueueManager(Settings.getProperty("HOST_MQ_QUEUE_MANAGER"));
        host.setFipsEnabled(IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty("HOST_FIPS_ENABLED")));
        final String hostSupportedMessageType = Settings.getString("HOST_SUPPORTED_MESSAGE_TYPE");
        if (hostSupportedMessageType != null &&
                !hostSupportedMessageType.isEmpty()) {
            StringTokenizer supportedMessageType = new StringTokenizer(hostSupportedMessageType, ",");
            while (supportedMessageType.hasMoreTokens()) {
                host.getSupportedMessageTypes().add(supportedMessageType.nextToken());
            }
        }
        return host;
    }

    private boolean isEligibleToAddHost(ClusterServerConfig clusterServerConfig) {
        if (isAlreadyAddedHost(clusterServerConfig)) {
            logger.info("New Host not Added. It is already added. OMS_ID:" + clusterServerConfig.getNodeId() + "OMS_IP:" + clusterServerConfig.getIp());
            return false;
        }
        if (isHostCountExceed()) {
            logger.error("New Host not Added. Host Count exceeded. Current count:" + Settings.getHostList().size());
            return false;
        }
        return true;
    }

    /*Check whether the host is already to added to DFIX*/
    private boolean isAlreadyAddedHost(ClusterServerConfig clusterServerConfig) {
        return Settings.getHostIdMap().containsKey((int) clusterServerConfig.getNodeId());  //node is short but map key in int
    }
    private boolean isHostCountExceed() {
        return Settings.getHostList().size() >= Settings.getInt(SettingsConstants.HOSTS_COUNT);
    }
}
