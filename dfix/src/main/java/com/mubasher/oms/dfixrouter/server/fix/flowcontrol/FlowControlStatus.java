package com.mubasher.oms.dfixrouter.server.fix.flowcontrol;

public enum FlowControlStatus {
    ALLOWED(""),
    DUPLICATE_SUSTAINED_BLOCKED("Request rejected due to Duplicate Sustained block"),
    NEW_ORDER_SUSTAINED_BLOCKED("Request rejected due to New Order Sustained block"),
    AMEND_ORDER_SUSTAINED_BLOCKED("Request rejected due to Amend Order Sustained block"),
    TEMPORARY_BLOCKED("Request rejected due to Temporary block"),
    CLIORDID_DUPLICATE_BLOCKED("Request rejected due to duplicate CliOrdId");


    String message;

    FlowControlStatus(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
