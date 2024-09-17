package com.mubasher.oms.dfixrouter.logs;

/**
 * Created by isurut on 7/3/2017.
 */
public enum LogEventsEnum {
    DEFAULT_EVENT("0"), REQUEST_RECEIVED("101"), SENT_TO_OMS("102"), REQUEST_RECEIVER_ERROR("103"),
    REQUEST_SENDER_ERROR("104"), SENT_TO_EXCHANGE("105"), RECEIVED_FRM_EXCHANGE("106");

    String eventID;

    LogEventsEnum(String eventID) {
        this.eventID = eventID;
    }
}
