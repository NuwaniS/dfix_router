package com.mubasher.oms.dfixrouter.beans;

import quickfix.Message;
import quickfix.SessionID;

/**
 * Created by randulal on 11/30/2018.
 */
public class InternalBean {
    private Message message;
    private SessionID sessionId;

    public InternalBean(Message message, SessionID sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public SessionID getSessionId() {
        return sessionId;
    }

    public void setSessionId(SessionID sessionId) {
        this.sessionId = sessionId;
    }
}
