package com.mubasher.oms.dfixrouter.server.oms;


import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

public class JMSHandler extends MiddlewareHandler {

    protected QueueConnectionFactory queueConnectionFactory = null;
    protected QueueConnection queueConnection = null;
    protected QueueSession queueSession = null;
    protected Queue myQueue = null;
    protected String contextFactory;
    protected String providerURL;
    protected String connectionFactory;
    protected String userName;
    protected String password;
    protected String queueName;
    protected String urlPkgPrefixes;
    protected String urlPkgPrefixesValue;
    protected String middleware;
    protected TextMessage textMessage;

    protected JMSHandler(Host host) {
        super(host);
        this.contextFactory = host.getContextFactory();
        this.providerURL = host.getProviderURL();
        this.connectionFactory = host.getConnectionFactory();
        this.urlPkgPrefixes = host.getUrlPkgPrefixes();
        this.urlPkgPrefixesValue = host.getUrlPkgPrefixesValue();
        this.userName = host.getUserName();
        this.password = decryptHostPassword(host.getPassword());
        middleware = host.getMiddleware();
    }

    protected QueueSession createQueueSession() throws NamingException, JMSException {
        Context context = getContext();
        queueConnectionFactory = (QueueConnectionFactory) context.lookup(connectionFactory);
        myQueue = (Queue) context.lookup(queueName);
        context.close();
        if (userName != null && password != null) {
            queueConnection = queueConnectionFactory.createQueueConnection(userName, password);   //send username and password for EAP
        } else {
            queueConnection = queueConnectionFactory.createQueueConnection();
        }
        queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
        queueConnection.setExceptionListener(exceptionListener);
        textMessage = queueSession.createTextMessage();
        return queueSession;
    }

    protected Context getContext() throws NamingException {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, contextFactory);
        props.put(Context.PROVIDER_URL, providerURL);
        if (IConstants.MIDDLEWARE_JMS.equalsIgnoreCase(middleware)) {
            props.put("jboss.naming.client.ejb.context", true);
            props.put("java.naming.rmi.security.manager", "yes");
        }
        props.put(urlPkgPrefixes, urlPkgPrefixesValue);
        if (userName != null && password != null) {
            props.put(Context.SECURITY_PRINCIPAL, userName);
            props.put(Context.SECURITY_CREDENTIALS, password);
        }
        return new InitialContext(props);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("JMSHandler{");
        sb.append("providerURL='").append(providerURL).append('\'');
        sb.append(", queueName='").append(queueName).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
