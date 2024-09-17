package com.mubasher.oms.dfixrouter.server.admin;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;
import quickfix.SessionID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by nuwanis on 12/16/2016.
 */
public class AdminManager {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.admin.AdminManager");
    private static final String PASSIVE_STATUS_RESPONSE = "Command is not allowed. DFIXRTR is in passive mode.";
    private static AdminManager adminManager = null;

    private AdminManager(){
        super();
    }

    public static AdminManager getInstance() {
        if (adminManager == null) {
            adminManager = new AdminManager();
        }
        return adminManager;
    }

    public String resetOutSequence(String sessionIdentifier, int seqNo) {
        if (DFIXRouterManager.getInstance().isStarted()) {
            try {
                FIXClient.getFIXClient().setOutGoingSeqNo(sessionIdentifier, seqNo);
            } catch (IOException e) {
                logger.error("Reset Out Sequence Failed: " + e.getMessage(), e);  //To change body of catch statement use File | Settings | File Templates.
            }
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("<").append(sessionIdentifier).append(":").append(-1).append(":").append(seqNo).append(">");
            updateSecondary(sb.toString());
        }
        return showStatus();
    }

    public void updateSecondary(String status) {
        FIXClient.getFIXClient().updateSessionSeqInSecondary(status);
    }

    public String showStatus() {
        StringBuilder sb = showStatusHeader();
        Iterator<SessionID> it = null;
        if (FIXClient.getFIXClient() != null
                && FIXClient.getSettings() != null) {
            it = FIXClient.getSettings().keySet().iterator();
        }
        while (it != null && it.hasNext()) {
            iterateSession(it, sb);
        }

        if (Settings.isSimulatorOn()
                && FIXClient.getFIXClient() != null
                && FIXClient.getSimSettings() != null) {
            it = FIXClient.getSimSettings().sectionIterator();
            while (it != null && it.hasNext()) {
                iterateSession(it, sb);
            }
        }
        return sb.toString();
    }

    private StringBuilder showStatusHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(IConstants.getStringFromat(Integer.max(FIXClient.getFIXClient().getLongestLengthSessionIdentifier() + IConstants.STRING_FORMAT_PAD, 10)), "Session"));
        sb.append(String.format(IConstants.getStringFromat(Integer.max(FIXClient.getFIXClient().getLongestLengthTargetCompId() + IConstants.STRING_FORMAT_PAD, 15)), "RemoteFirmID"));
        sb.append(String.format(IConstants.getStringFromat(Integer.max(FIXClient.getFIXClient().getLongestLengthSenderCompId() + IConstants.STRING_FORMAT_PAD, 15)), "LocalFirmID"));
        sb.append(String.format(IConstants.getStringFromat(15), "Status"));
        sb.append(String.format(IConstants.getStringFromat(10), "SeqNumIn"));
        sb.append(String.format(IConstants.getStringFromat(10), "SeqNumOut"));
        sb.append("\n");
        int dashes = Integer.max(FIXClient.getFIXClient().getLongestLengthSessionIdentifier() + IConstants.STRING_FORMAT_PAD, 10) +
                Integer.max(FIXClient.getFIXClient().getLongestLengthTargetCompId() + IConstants.STRING_FORMAT_PAD, 15) +
                Integer.max(FIXClient.getFIXClient().getLongestLengthSenderCompId() + IConstants.STRING_FORMAT_PAD, 15) +
                35;// Status(15) + SeqNumIn(10) + SeqNumOut(10)
        for (int i = 0; i < dashes; i++) {
            sb.append("-");
        }
        sb.append("\n");
        return sb;
    }

    private void iterateSession(Iterator<SessionID> it, StringBuilder sb) {
        SessionID sessionID = it.next();
        sb.append(String.format(IConstants.getStringFromat(Integer.max(FIXClient.getFIXClient().getLongestLengthSessionIdentifier() + IConstants.STRING_FORMAT_PAD, 10)),
                FIXClient.getFIXClient().getAllSessionProperty(sessionID, IConstants.SESSION_IDENTIFIER)));
        sb.append(String.format(IConstants.getStringFromat(Integer.max(FIXClient.getFIXClient().getLongestLengthTargetCompId() + IConstants.STRING_FORMAT_PAD, 15)),
                sessionID.getTargetCompID()));
        sb.append(String.format(IConstants.getStringFromat(Integer.max(FIXClient.getFIXClient().getLongestLengthSenderCompId() + IConstants.STRING_FORMAT_PAD, 15)),
                sessionID.getSenderCompID()));
        sb.append(String.format(IConstants.getStringFromat(15), FIXClient.getFIXClient().getSessionStatus(sessionID)));
        sb.append(String.format(IConstants.getStringFromat(10), FIXClient.getFIXClient().getExpectedTargetSeqNumber(sessionID)));
        sb.append(String.format(IConstants.getStringFromat(10), FIXClient.getFIXClient().getExpectedSenderSeqNumber(sessionID)));
        sb.append("\n");
    }

    public String resetInSequence(String sessionIdentifier, int seqNo) {
        if (DFIXRouterManager.getInstance().isStarted()) {
            try {
                FIXClient.getFIXClient().setInComingSeqNo(sessionIdentifier, seqNo);
            } catch (IOException e) {
                logger.error("Reset In Sequence Failed: " + e.getMessage(), e);  //To change body of catch statement use File | Settings | File Templates.
            }
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("<").append(sessionIdentifier).append(":").append(seqNo).append(":").append(-1).append(">");
            updateSecondary(sb.toString());
        }
        return showStatus();
    }

    public String disconnectSession(String sessionIdentifier) {
        String output = PASSIVE_STATUS_RESPONSE;
        if (DFIXRouterManager.getInstance().isStarted()) {
            FIXClient.getFIXClient().disconnectSession(sessionIdentifier);
            output = showStatus();
        } else {
            logger.info(output);
        }
        return output;
    }

    public String connectSession(String sessionIdentifier) {
        String output = PASSIVE_STATUS_RESPONSE;
        if (DFIXRouterManager.getInstance().isStarted()) {
            FIXClient.getFIXClient().connectSession(sessionIdentifier);
            output = showStatus();
        } else {
            logger.info(output);
        }
        return output;
    }

    public String runEod(String sessionIdentifier) {
        String output = PASSIVE_STATUS_RESPONSE;
        if (DFIXRouterManager.getInstance().isStarted()) {
            FIXClient.getFIXClient().runEOD(sessionIdentifier);
            output = "EOD Command Executed.";
        } else {
            logger.info(output);
        }
        return output;
    }

    public String startDFIXRouter() {
        String output = null;
        try {
            output = DFIXRouterManager.getInstance().startDFIXRouterManager();
        } catch (Exception e) {
            logger.error("Activate Command Failed: " + e.getMessage(), e);
        }
        return output;
    }

    public String stopDFIXRouter() {
        DFIXRouterManager.setIsGraceFulClose(false);
        return DFIXRouterManager.getInstance().stopDFIXRouterManager();
    }

    public String sendSessionList() {
        return FIXClient.getFIXClient().getSessionList();
    }

    public String sendSessionSequence() {
        if (DFIXRouterManager.getInstance().isStarted()) {
            StringBuilder sb = new StringBuilder(IConstants.STRING_SEQUENCE);
            ArrayList<SessionID> sessionList = (ArrayList<SessionID>) FIXClient.getFIXClient().getInitiatorSessionList();
            sessionList.addAll(FIXClient.getFIXClient().getAcceptorSessionList());

            for (SessionID sesID : sessionList) {
                sb.append("<")
                        .append(FIXClient.getFIXClient().getSessionProperty(sesID, IConstants.SESSION_IDENTIFIER))
                        .append(":")
                        .append(FIXClient.getFIXClient().getExpectedTargetSeqNumber(sesID))
                        .append(":")
                        .append(FIXClient.getFIXClient().getExpectedSenderSeqNumber(sesID))
                        .append(">");
            }
            return sb.toString();
        } else {
            logger.info("Send session sequence failed. DFIXRTR is in passive mode.");
            return null;
        }
    }
}
