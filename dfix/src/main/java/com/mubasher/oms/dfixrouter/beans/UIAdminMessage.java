package com.mubasher.oms.dfixrouter.beans;

import com.mubasher.oms.dfixrouter.constants.IConstants;

public class UIAdminMessage {
    private String exchange;
    private String message;

    public void parseMessage(String msg) {
        String[] data = msg.split(IConstants.FS);
        exchange = data[0];
        message = data[1];
    }

    public String composeMessage() {
        return exchange + IConstants.FS + message + '\n';
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
