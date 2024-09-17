package com.mubasher.oms.dfixrouter.beans;

import com.mubasher.oms.dfixrouter.constants.IConstants;

/**
 * this is a class used to keep the message obtained from OMS queues
 */
public class DFIXMessage {
    private String exchange = "";
    private String message = "";
    private String type = "";
    private String sequence = "";
    private int fixTag10008 = -1;
    private int servedBy = -1;
    private String msgGroupId = "0";   //Group the messages for sequential processing by MDB parallel consumers - for application messages
    private int interQueueId = -1;
    private String fixMsgType = null;//FixTag: 35
    private boolean isSimProcessing = false;
    private String connectionName = "";

    public String composeMessage() {
        StringBuilder sb = new StringBuilder(exchange);
        sb.append(IConstants.FS).append(sequence).append(IConstants.FS).append(type).append(IConstants.FS).append(message).append(IConstants.FS).append(connectionName);
        return sb.toString();
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

    public String getSequence() {
        return sequence;
    }

    public void setSequence(String sequence) {
        this.sequence = sequence;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getFixTag10008() {
        return fixTag10008;
    }

    public void setFixTag10008(int fixTag10008) {
        this.fixTag10008 = fixTag10008;
    }

    public int getServedBy() {
        return servedBy;
    }

    public void setServedBy(int servedBy) {
        this.servedBy = servedBy;
    }

    public String getMsgGroupId() {
        return msgGroupId;
    }

    public void setMsgGroupId(String msgGroupId) {
        this.msgGroupId = msgGroupId;
    }

    public int getInterQueueId() {
        return interQueueId;
    }

    public void setInterQueueId(int interQueueId) {
        this.interQueueId = interQueueId;
    }

    public String getFixMsgType() {
        return fixMsgType;
    }

    public void setFixMsgType(String fixMsgType) {
        this.fixMsgType = fixMsgType;
    }

    public boolean isSimProcessing() {
        return isSimProcessing;
    }

    public void setSimProcessing(boolean isSimProcessing) {
        this.isSimProcessing = isSimProcessing;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DFIXMessage{");
        sb.append("exchange='").append(exchange).append('\'');
        sb.append(", message='").append(message).append('\'');
        sb.append(", type='").append(type).append('\'');
        sb.append(", sequence='").append(sequence).append('\'');
        sb.append(", fixTag10008=").append(fixTag10008);
        sb.append(", servedBy=").append(servedBy);
        sb.append(", msgGroupId='").append(msgGroupId).append('\'');
        sb.append(", interQueueId=").append(interQueueId);
        sb.append(", fixMsgType='").append(fixMsgType).append('\'');
        sb.append(", isSimProcessing=").append(isSimProcessing);
        sb.append('}');
        return sb.toString();
    }
}
