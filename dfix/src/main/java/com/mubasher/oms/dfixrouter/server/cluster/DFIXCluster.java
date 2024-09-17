package com.mubasher.oms.dfixrouter.server.cluster;

import com.mubasher.oms.dfixrouter.beans.ClusterMessage;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.exception.LicenseException;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.failover.FailOverHandler;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.jgroups.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by nuwanis on 11/10/2016.
 */
public class DFIXCluster extends ReceiverAdapter {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.cluster.DFIXCluster");

    private static JChannel channel;
    private static List<Address> previousNodeList = new ArrayList<>();
    private static DFIXCluster dfixCluster;
    private final byte allowedMaxNodes;
    private String primaryNode = null;      //activated node
    private boolean isPrimary = false;      //activated status
    private boolean isSingleNodeYet = false;
    private String memberName = SettingsConstants.CLUSTER_MEMBER_PREFIX + Settings.getProperty(IConstants.SETTING_DFIX_ID);
    private String clusterName = Settings.getProperty(SettingsConstants.CLUSTER_NAME_DFIX);
    private FIXClient fixClient = FIXClient.getFIXClient();

    private DFIXCluster(byte allowedMaxNodes) {
        this.allowedMaxNodes = allowedMaxNodes;
        if (allowedMaxNodes == 1) {
            isSingleNodeYet = true;
        } else {
            try {
                logger.info("Connecting to the DFIX Cluster");
                setChannel(new JChannel("jgroups.xml"));
                channel.setReceiver(this);
                channel.setName(memberName);
                channel.connect(clusterName);
                channel.setDiscardOwnMessages(true);
                setPreviousNodeList(channel.getView().getMembers());

                if (previousNodeList.size() == 1) {
                    isSingleNodeYet = true;
                } else {
                    isSingleNodeYet = false;
                    askWhoIsPrimary();
                }
            } catch (Exception e) {
                logger.error("DFIX Cluster Starting Failed: " + e.getMessage(), e);
            }
        }
    }

    public static void setChannel(JChannel channel) {
        DFIXCluster.channel = channel;
    }

    public static void setPreviousNodeList(List<Address> previousNodeList) {
        DFIXCluster.previousNodeList = previousNodeList;
    }


    static List<Address> getPreviousNodeList() {
        return previousNodeList;
    }

    public void askWhoIsPrimary() {
        validateAllowedMaxNodes();
        ClusterMessage clusterMessage = new ClusterMessage(ClusterMessage.TYPE.WHO_IS_PRIMARY);
        try {
            sendMessage(clusterMessage);
        } catch (Exception e) {
            logger.error("WHO_IS_PRIMARY Cluster message sending failed: " + e.getMessage(), e);
        }
    }

    private void validateAllowedMaxNodes() {
        int currentNodeSize = channel.getView().size();
        logger.debug("Validating Max Nodes: " + allowedMaxNodes + " against Current Nodes size: " + currentNodeSize);
        if (allowedMaxNodes != 0 && allowedMaxNodes < currentNodeSize) {
            logger.error((new LicenseException(LicenseException.ALLOWED_PARALLEL_DFIX_FAIL, Integer.toString(allowedMaxNodes), Integer.toString(currentNodeSize))).getMessage());
            DFIXRouterManager.setIsGraceFulClose(false);
            DFIXRouterManager.exitApplication(0);
        }
    }

    private void sendMessage(ClusterMessage event) throws Exception {
        if (isSingleNodeYet()) {
            logger.debug("Single DFIXRTR in the cluster : Message sending ignored " + event.toString());
        } else {
            try {
                Message msg = new Message(null, event);
                channel.send(msg);
                logger.debug("Message sent to Cluster: " + event.toString());
            } catch (Exception e) {
                logger.error("Cluster Message Sending Failed: " + e.getMessage(), e);
                throw e;
            }
        }
    }

    public static DFIXCluster getInstance() {
        if (dfixCluster == null) {
            dfixCluster = getInstance((byte) 1);
        }
        return dfixCluster;
    }

    public static DFIXCluster getInstance(byte allowedMaxNodes) {
        if (dfixCluster == null) {
            dfixCluster = new DFIXCluster(allowedMaxNodes);
        }
        return dfixCluster;
    }

    public void sendClusterMessage(ClusterMessage msg) throws Exception {
        sendMessage(msg);
    }

    @Override
    public void receive(Message msg) {
        Object messageObject = msg.getObject();
        if (messageObject instanceof ClusterMessage) {
            ClusterMessage message = (ClusterMessage) messageObject;
            logger.info("Cluster message received: " + message.toString());
            String node = msg.getSrc() + "";
            switch (message.getType()) {
                case PRIMARY_STATUS_CHANGE:     //only sent by primary
                    updatePrimaryStatus(node, message);
                    break;
                case REFRESH_OWN_STATUS:        //only received by secondary
                    String sequence = message.getSequence();
                    primaryNode = node;
                    fixClient.updateSessionSeqInSecondary(sequence);
                    break;
                case WHO_IS_PRIMARY:            //when a new member joins - answered by primary
                    tellWhoIsPrimary();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void viewAccepted(View view) {       //do not send messages from this method
        List<Address> currentNodeList = view.getMembers();

        if (currentNodeList.size() > previousNodeList.size()) {     //new member joins
            isSingleNodeYet = false;
        } else {
            for (Address address : previousNodeList) {      //find the members who left
                if (!view.containsMember(address) && address.toString().equals(primaryNode)) {
                    primaryNode = "";
                    logger.error("There is no activated DFIXRTR in the cluster");
                    startFailOverProcess();
                    break;
                }
            }
            if (currentNodeList.size() == 1) {
                isSingleNodeYet = true;
            }
        }
        setPreviousNodeList(currentNodeList);
    }

    public void startFailOverProcess() {
        if (IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.AUTO_FAILOVER_ENABLED))) {
            FailOverHandler failOverHandler = new FailOverHandler();
            failOverHandler.start();
        } else {
            logger.info("Auto FailOver Handling not enabled");
        }
    }

    private void updatePrimaryStatus(String node, ClusterMessage clusterMessage) {
        boolean primaryStatus = clusterMessage.isPrimaryStatus();
        isPrimary = false;      //assuming only secondaries receive this message
        if (primaryStatus) {
            primaryNode = node;
            logger.info("Updated primary DFIXRTR :" + node + " Status: UP");
        } else {
            if (primaryNode.equalsIgnoreCase(node)) {
                primaryNode = "";       //clear if a primary is set
                logger.info("Updated primary DFIXRTR :" + node + " Status: DOWN");
                logger.error("There is no activated DFIXRTR in the cluster");
                startFailOverProcess();
            }
        }
    }

    public void tellWhoIsPrimary() {
        if (isPrimary) {
            tellClusterMyStatus(IConstants.CONSTANT_TRUE);
        }
    }

    public void tellClusterMyStatus(boolean status) {
        if (status) {
            primaryNode = memberName;
        } else {
            primaryNode = "";
        }
        isPrimary = status;
        ClusterMessage clusterMessage = new ClusterMessage(ClusterMessage.TYPE.PRIMARY_STATUS_CHANGE, status);
        logger.info("Notifies Cluster about my status : IS_PRIMARY-" + status);
        try {
            sendMessage(clusterMessage);
        } catch (Exception e) {
            logger.error("PRIMARY_STATUS_CHANGE Cluster Message Sending Failed: " + e.getMessage(), e);
        }
    }

    public void disconnect() {
        try {
            channel.close();
        } catch (Exception e) {
            logger.error("DFIX Cluster disconnect Failed: " + e.getMessage(), e);
        }
    }

    public String getPrimaryNode() {
        return primaryNode;
    }

    public void setPrimaryNode(String primaryNode) {
        this.primaryNode = primaryNode;
    }

    public boolean isAllowedToActivate() {
        if (primaryNode == null || "".equals(primaryNode) || primaryNode.equalsIgnoreCase(memberName)) {
            return IConstants.CONSTANT_TRUE;
        }
        return IConstants.CONSTANT_FALSE;
    }

    public boolean isSingleNodeYet() {
        return isSingleNodeYet;
    }
}
