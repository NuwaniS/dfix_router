package com.mubasher.oms.dfixrouter.server.failover;

import org.junit.jupiter.api.Test;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import org.mockito.MockedStatic;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;

import static org.mockito.Mockito.*;

/**
 * Created by Nilaan L on 5/17/2024.
 */
class DisconnectedSessionMessageHandlerTest {

    @Test
    void DisconnectedSessionMessageHandlerRunTest() {
        //Arrange
        String sessionIdentifier = "session1";
        FIXClient fixClient = mock(FIXClient.class);
        MockedStatic<FIXClient> fixClientMockedStatic = mockStatic(FIXClient.class);
        fixClientMockedStatic.when(()->FIXClient.getFIXClient()).thenReturn(fixClient);
        MockedStatic<Session> sessionMockedStatic = mockStatic(Session.class);
        SessionID sessionId = new SessionID("FIX.4.4", "SenderCompID", "TargetCompID");
        Message msg = mock(Message.class);
        when(fixClient.getSessionID(sessionIdentifier)).thenReturn(sessionId);
        final com.objectspace.jgl.Queue queue = new com.objectspace.jgl.Queue();
        queue.add(msg);
        when(fixClient.getPendingMessages(sessionIdentifier)).thenReturn(queue);
        DisconnectedSessionMessageHandler handler = new DisconnectedSessionMessageHandler(sessionIdentifier);

        // Act
        handler.run();

        // Assert
        verify(fixClient, times(4)).getPendingMessages(sessionIdentifier);
        sessionMockedStatic.verify(()->Session.sendToTarget(msg, sessionId),times(1));

        //clean
        fixClientMockedStatic.close();
        sessionMockedStatic.close();

    }
}
