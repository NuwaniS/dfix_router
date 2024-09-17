package com.mubasher.oms.dfixrouter.server.oms;

import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.jms.JmsConstants;
import com.ibm.msg.client.wmq.common.CommonConstants;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.system.Settings;

import javax.jms.*;

public class MQHandler extends MiddlewareHandler {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.oms.MQHandler");
    protected int port;
    protected String queueName;
    protected QueueConnection connection;
    protected Session session;
    protected Destination destination;
    protected String channel;
    private String mqQueueManager;
    private String sslCipherSuite;
    private boolean isFipsEnabled;
    private String userName;
    private String password;

    protected MQHandler(Host host) {
        super(host);
        this.port = host.getPort();
        this.channel = host.getChannel();
        this.mqQueueManager = host.getMqQueueManager();
        this.sslCipherSuite = host.getSSLCipherSuite();
        this.isFipsEnabled = host.isFipsEnabled();
        this.userName = host.getUserName();
        this.password = decryptHostPassword(host.getPassword());
    }

    protected void createSession(String queueName) throws JMSException {
        try {
            if (userName != null && password != null) {
                connection = getQueueConnectionFactory().createQueueConnection(userName, password);
            } else {
                connection = getQueueConnectionFactory().createQueueConnection();
            }
            session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            destination = createDestination(queueName);
        } catch (JMSException e) {
            logger.error("Failed to create queue session ", e);
            throw e;
        }
    }

    protected QueueConnectionFactory getQueueConnectionFactory() throws JMSException {
        String connectionNameList = ip + "(" + port + ")";
        logger.info("ConnectionNameList, " + connectionNameList);
        MQQueueConnectionFactory cf = new MQQueueConnectionFactory();
        cf.setChannel(channel);
        cf.setTransportType(CommonConstants.WMQ_CM_CLIENT);
        cf.setConnectionNameList(connectionNameList);
        // if ClientReconnectOptions is set to WMQ_CLIENT_RECONNECT then MQ client internally handle connection failure
        // and does  not call the onExceptionListener we configured to handle queue exception manually
        cf.setClientReconnectOptions(CommonConstants.WMQ_CLIENT_RECONNECT_DISABLED);
        cf.setHostName(ip);
        if (mqQueueManager != null && !mqQueueManager.isEmpty()) {
            cf.setQueueManager(mqQueueManager);
        }
        if (userName != null && !userName.isEmpty()) {
            cf.setStringProperty(JmsConstants.USERID, userName);
        }
        if (password != null && !password.isEmpty()) {
            cf.setStringProperty(JmsConstants.PASSWORD, password);
        }
        if (System.getProperties().keySet().contains(IConstants.SSL_KEY_STORE_ARG)
                && System.getProperties().keySet().contains(IConstants.SSL_KEY_STORE_PASS_ARG)
                && !System.getProperty(IConstants.SSL_KEY_STORE_ARG).equals("NONE")) {
            cf.setSSLCipherSuite(sslCipherSuite);
            cf.setSSLFipsRequired(isFipsEnabled);
        }
        return cf;
    }

    protected Destination createDestination(String queueName) throws JMSException {
        logger.info("IP:" + ip + " Port:" + port + " DestinationQueueName:" + queueName);
        Destination dest = session.createQueue(queueName);
        if (dest instanceof com.ibm.mq.jms.MQQueue) {
            if (IConstants.SETTING_YES.equals(Settings.getProperty(SettingsConstants.IS_MQ_CLUSTER))) {
                // when dfix is connecting to MQ cluster configuration ,
                // JMS compliant is enabled to include custom message Properties
                ((com.ibm.mq.jms.MQQueue) dest).setTargetClient(CommonConstants.WMQ_CLIENT_JMS_COMPLIANT);
            } else {
                ((com.ibm.mq.jms.MQQueue) dest).setTargetClient(CommonConstants.WMQ_CLIENT_NONJMS_MQ);
            }
        }
        return dest;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MQHandler{");
        sb.append("ip='").append(ip).append('\'');
        sb.append(", port=").append(port);
        sb.append(", queueName='").append(queueName).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
