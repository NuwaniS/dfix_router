package com.mubasher.oms.dfixrouter.beans;

import java.io.Serializable;

/**
 * Created by nuwanis on 11/10/2016.
 */
public class ClusterMessage implements Serializable {

    private TYPE type;
    private boolean primaryStatus = false;
    private String sequence;

    public ClusterMessage(TYPE type) {
        this.type = type;
    }

    public ClusterMessage(TYPE type, String sequence) {
        this.type = type;
        this.sequence = sequence;
    }

    public ClusterMessage(TYPE type, boolean primaryStatus) {
        this.type = type;
        this.primaryStatus = primaryStatus;

    }

    public boolean isPrimaryStatus() {
        return primaryStatus;
    }

    public TYPE getType() {
        return type;
    }

    public String getSequence() {
        return sequence;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ClusterMessage{");
        sb.append("type=").append(type);
        sb.append(", primaryStatus=").append(primaryStatus);
        sb.append(", sequence='").append(sequence).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public enum TYPE {
        PRIMARY_STATUS_CHANGE, REFRESH_OWN_STATUS, WHO_IS_PRIMARY;
    }
}
