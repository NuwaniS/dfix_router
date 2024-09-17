package com.mubasher.oms.dfixrouter.beans;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

public class IpDuration implements Serializable {
    private String ip;
    private long expiryDate;
    private boolean isForever = false;

    protected IpDuration() {
        /*Default Constructor*/
    }

    public IpDuration(String ip) {
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    public Date getExpiryDate() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(expiryDate);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        return cal.getTime();
    }

    public void setExpiryDate(long expiryDate) {
        this.expiryDate = expiryDate;
    }

    public boolean isForever() {
        return isForever;
    }

    public void setForever(boolean forever) {
        isForever = forever;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IpDuration{");
        sb.append("ip='").append(ip).append('\'');
        sb.append(", expiryDate=").append(expiryDate);
        sb.append(", isForever=").append(isForever);
        sb.append('}');
        return sb.toString();
    }
}
