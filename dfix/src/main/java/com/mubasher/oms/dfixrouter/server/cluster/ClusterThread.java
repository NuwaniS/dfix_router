package com.mubasher.oms.dfixrouter.server.cluster;

import com.mubasher.oms.dfixrouter.beans.ClusterMessage;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.admin.UIAdminServer;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import quickfix.Session;
import quickfix.SessionID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by nuwanis on 11/15/2016.
 */
public class ClusterThread implements Runnable {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.cluster.ClusterThread");
    private static FIXClient fixClient = FIXClient.getFIXClient();
    private static ConcurrentHashMap<String,Session> sessionMap = new ConcurrentHashMap<>();
    DFIXRouterManager dfixRouterManager = DFIXRouterManager.getInstance();
    DFIXCluster dfixCluster = DFIXCluster.getInstance();
    String sequence;

    public ClusterThread() {
        loadSessions();
    }

    private static void loadSessions() {
        for (SessionID sessionID : fixClient.getInitiatorSessionList()) {
            sessionMap.put(fixClient.getSessionProperty(sessionID, IConstants.SESSION_IDENTIFIER), Session.lookupSession(sessionID));
        }
    }

    @Override
    public void run() {
        if (dfixRouterManager.isStarted()) {
            sequence = getSequence();
            if (!dfixCluster.isSingleNodeYet()) {
                logger.debug("Updating sequence numbers");
                sendSequence(sequence);
            }
            if (UIAdminServer.getClients().size() > 0) {
                UIAdminServer.sendToAdminClients(IConstants.ALL_SESSIONS, IConstants.STRING_SEQUENCE + sequence);
            }
        }
    }

    public String getSequence() {       //<TDWL:25:30><NSDQ:25:30>
        StringBuilder sb = new StringBuilder();
        try {
            Iterator<Map.Entry<String, Session>> sessionEntry = sessionMap.entrySet().iterator();
            while (sessionEntry.hasNext()) {
                Map.Entry<String, Session> session = sessionEntry.next();
                sb.append("<")
                        .append(session.getKey()).append(":")
                        .append(session.getValue().getExpectedTargetNum()).append(":")
                        .append(session.getValue().getExpectedSenderNum())
                        .append(">");
            }
        } catch (Exception e) {
            logger.error("Error at building sequence info: " + e.getMessage(), e);
        }
        return sb.toString();
    }

    private void sendSequence(String seq) {
        ClusterMessage clusterMessage = new ClusterMessage(ClusterMessage.TYPE.REFRESH_OWN_STATUS, seq);
        try {
            dfixCluster.sendClusterMessage(clusterMessage);
        } catch (Exception e) {
            logger.error("Error Sending Sequence to Cluster" + e.getMessage(), e);
        }
    }

    public void resetSessions() {
        sessionMap.clear();
        loadSessions();
    }
}
