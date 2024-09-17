package com.mubasher.oms.dfixrouter.beans;

import java.io.Serializable;
import java.util.*;

public class License implements Serializable {

    private Map<String, IpDuration> ipDurations = new LinkedHashMap<>();
    private byte allowedSessions;
    private long licenseStartDate;
    private byte allowedParallelDfixes;

    public License() {
        /*Default Constructor*/
    }

    public License(long licenseStartDate) {
        this.licenseStartDate = licenseStartDate;
    }

    public byte getAllowedSessions() {
        return allowedSessions;
    }

    public void setAllowedSessions(byte allowedSessions) {
        this.allowedSessions = allowedSessions;
    }

    public Set<String> getAllowedIPs() {
        return ipDurations.keySet();
    }

    public IpDuration getIpDuration(String ip) {
        return ipDurations.get(ip);
    }

    public void setIpDurations(Map<String, IpDuration> ipDurations) {
        this.ipDurations = ipDurations;
    }

    public Date getLicenseStartDate() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(licenseStartDate);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        return cal.getTime();
    }

    public void setLicenseStartDate(long licenseStartDate) {
        this.licenseStartDate = licenseStartDate;
    }

    public void addIpDuration(IpDuration ipDuration) {
        this.ipDurations.put(ipDuration.getIp(), ipDuration);
    }

    public byte getAllowedParallelDfixes() {
        return allowedParallelDfixes;
    }

    public void setAllowedParallelDfixes(byte allowedParallelDfixes) {
        this.allowedParallelDfixes = allowedParallelDfixes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("License{");
        sb.append("ipDurations=").append(ipDurations);
        sb.append(", allowedSessions=").append(allowedSessions);
        sb.append(", licenseStartDate=").append(licenseStartDate);
        sb.append(", allowedParallelDfixes=").append(allowedParallelDfixes);
        sb.append('}');
        return sb.toString();
    }
}
