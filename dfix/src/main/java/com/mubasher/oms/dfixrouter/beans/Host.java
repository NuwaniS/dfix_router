package com.mubasher.oms.dfixrouter.beans;

import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: muhammadha
 * Date: 25-10-2016
 * Time: 4:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class Host {
    int id = 1;
    int omsId = 1;  //Used when the oms id is different than the host id in the total solution, Currently used when watchdog is enabled
    String ip = null;
    int port = -1;
    String reqQName = null;
    String resQName = null;
    String clubQName = null;
    String middleware = null;
    String contextFactory = null;
    String providerURL = null;
    String connectionFactory = null;
    String userName = null;
    String password = null;
    String intermediateQueue = null;
    String uMessageQueue = null;
    int intermediateQueueCount = 0;
    String channel = null;
    String urlPkgPrefixes = null;
    String urlPkgPrefixesValue = null;
    int reqQCount = 0;
    Set<String> supportedMessageTypes = new HashSet<>();
    String mqQueueManager;
    String sSLCipherSuite;
    boolean isFipsEnabled;
    int icmDropCopyQueueCount = 0;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOmsId() {
        return omsId;
    }

    public void setOmsId(int omsId) {
        this.omsId = omsId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getReqQName() {
        return reqQName;
    }

    public void setReqQName(String reqQName) {
        this.reqQName = reqQName;
    }

    public String getResQName() {
        return resQName;
    }

    public void setResQName(String resQName) {
        this.resQName = resQName;
    }

    public String getClubQName() {
        return clubQName;
    }

    public void setClubQName(String clubQName) {
        this.clubQName = clubQName;
    }

    public String getMiddleware() {
        return middleware;
    }

    public void setMiddleware(String middleware) {
        this.middleware = middleware;
    }

    public String getContextFactory() {
        return contextFactory;
    }

    public void setContextFactory(String contextFactory) {
        this.contextFactory = contextFactory;
    }

    public String getProviderURL() {
        return providerURL;
    }

    public void setProviderURL(String providerURL) {
        this.providerURL = providerURL;
    }

    public String getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(String connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getIntermediateQueue() {
        return intermediateQueue;
    }

    public void setIntermediateQueue(String intermediateQueue) {
        this.intermediateQueue = intermediateQueue;
    }

    public int getIntermediateQueueCount() {
        return intermediateQueueCount;
    }

    public void setIntermediateQueueCount(int intermediateQueueCount) {
        this.intermediateQueueCount = intermediateQueueCount;
    }

    public String getuMessageQueue() {
        return uMessageQueue;
    }

    public void setuMessageQueue(String uMessageQueue) {
        this.uMessageQueue = uMessageQueue;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getUrlPkgPrefixes() {
        return urlPkgPrefixes;
    }

    public void setUrlPkgPrefixes(String urlPkgPrefixes) {
        this.urlPkgPrefixes = urlPkgPrefixes;
    }

    public String getUrlPkgPrefixesValue() {
        return urlPkgPrefixesValue;
    }

    public void setUrlPkgPrefixesValue(String urlPkgPrefixesValue) {
        this.urlPkgPrefixesValue = urlPkgPrefixesValue;
    }

    public int getReqQCount() {
        return reqQCount;
    }

    public void setReqQCount(int reqQCount) {
        this.reqQCount = reqQCount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Host{");
        sb.append("id=").append(id);
        sb.append(", omsId=").append(omsId);
        sb.append(", ip='").append(ip).append('\'');
        sb.append(", port=").append(port);
        sb.append('}');
        return sb.toString();
    }

    public Set<String> getSupportedMessageTypes() {
        return supportedMessageTypes;
    }

    public void setSupportedMessageTypes(Set<String> supportedMessageTypes) {
        this.supportedMessageTypes = supportedMessageTypes;
    }

    public String getMqQueueManager() {
        return mqQueueManager;
    }

    public void setMqQueueManager(String mqQueueManager) {
        this.mqQueueManager = mqQueueManager;
    }

    public String getSSLCipherSuite() {
        return sSLCipherSuite;
    }

    public void setSSLCipherSuite(String sSLCipherSuite) {
        this.sSLCipherSuite = sSLCipherSuite;
    }

    public boolean isFipsEnabled() {
        return isFipsEnabled;
    }

    public void setFipsEnabled(boolean fipsEnabled) {
        isFipsEnabled = fipsEnabled;
    }

    public int getIcmDropCopyQueueCount() {
        return icmDropCopyQueueCount;
    }

    public void setIcmDropCopyQueueCount(int icmDropCopyQueueCount) {
        this.icmDropCopyQueueCount = icmDropCopyQueueCount;
    }
}
