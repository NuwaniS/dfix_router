package com.mubasher.oms.dfixrouter.server.failover;

import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;

public class DisconnectedSessionMessageHandler implements Runnable {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.failover.DisconnectedSessionMessageHandler");
    private final SessionID sessionId;
    private final String sessionIdentifier;

    public DisconnectedSessionMessageHandler(String sessionIdentifier) {
        super();
        this.sessionIdentifier = sessionIdentifier;
        sessionId = FIXClient.getFIXClient().getSessionID(sessionIdentifier);
    }

    @Override
    public void run() {
        while (FIXClient.getFIXClient().getPendingMessages(sessionIdentifier).size() > 0) {
            try {
                Message message = (Message) FIXClient.getFIXClient().getPendingMessages(sessionIdentifier).front();
                Session.sendToTarget(message, sessionId);
                FIXClient.getFIXClient().getPendingMessages(sessionIdentifier).pop();// get poped only if no exceptions
                logger.debug("Session: " + sessionId + " reconnected. Message sent:" + message.toString());
            } catch (Exception exception) {
                logger.error("Error at FIX Session Message Send at reconnection: " + exception.getMessage(), exception);
            }
        }
    }
}
