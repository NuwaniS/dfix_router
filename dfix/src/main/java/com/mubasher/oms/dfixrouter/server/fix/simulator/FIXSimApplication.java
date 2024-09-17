package com.mubasher.oms.dfixrouter.server.fix.simulator;

import com.mubasher.oms.dfixrouter.beans.OrderBean;
import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.SimulatorSettings;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.fix.FIXApplicationCommonLogic;
import com.mubasher.oms.dfixrouter.system.Settings;
import quickfix.*;
import quickfix.Message;
import quickfix.field.*;
import quickfix.fix42.*;
import quickfix.fix43.TradeCaptureReport;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by manodyas on 10/31/2017.
 */
public class FIXSimApplication extends FIXApplicationCommonLogic implements Application {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.simulator.FIXSimApplication");
    private SessionSettings settings;
    private ExecutorService negDealExecutor;

    public FIXSimApplication(SessionSettings sessionSettings) {
        this.settings = sessionSettings;
    }

    /**
     * This method is called when quickfix creates a new session. A session
     * comes into and remains in existence for the life of the application.
     * Sessions exist whether or not a counter party is connected to it. As soon
     * as a session is created, you can begin sending messages to it. If no one
     * is logged on, the messages will be sent at the time a connection is
     * established with the counterparty.
     *
     * @param sessionId QuickFIX session ID
     */
    @Override
    public void onCreate(SessionID sessionId) {
        logger.info("FIXSimApplication : onCreate(" + sessionId + ")");
    }

    /**
     * This callback notifies you when a valid logon has been established with a
     * counter party. This is called when a connection has been established and
     * the FIX logon process has completed with both parties exchanging valid
     * logon messages.
     *
     * @param sessionId QuickFIX session ID
     */
    @Override
    public void onLogon(SessionID sessionId) {
        logger.info("FIXSimApplication : onLogon(" + sessionId + ")");
    }

    /**
     * This callback notifies you when an FIX session is no longer online. This
     * could happen during a normal logout exchange or because of a forced
     * termination or a loss of network connection.
     *
     * @param sessionId QuickFIX session ID
     */
    @Override
    public void onLogout(SessionID sessionId) {
        logger.info("FIXSimApplication : onLogout(" + sessionId + ")");
    }

    /**
     * This callback provides you with a peek at the administrative messages
     * that are being sent from your FIX engine to the counter party. This is
     * normally not useful for an application however it is provided for any
     * logging you may wish to do. You may add fields in an adminstrative
     * message before it is sent.
     *
     * @param message   QuickFIX message
     * @param sessionId QuickFIX session ID
     */
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        logger.info("FIXSimApplication : toAdmin(" + message.toString() + "," + sessionId + ")");

    }

    /**
     * This callback notifies you when an administrative message is sent from a
     * counterparty to your FIX engine. This can be usefull for doing extra
     * validation on logon messages such as for checking passwords. Throwing a
     * RejectLogon exception will disconnect the counterparty.
     *
     * @param message   QuickFIX message
     * @param sessionId QuickFIX session ID
     * @throws FieldNotFound
     * @throws IncorrectDataFormat
     * @throws IncorrectTagValue
     * @throws RejectLogon         causes a logon reject
     */
    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        logger.debug("FIXSimApplication : fromAdmin(" + message.toString() + "," + sessionId + ")");
    }

    /**
     * This is a callback for application messages that you are sending to a
     * counterparty. If you throw a DoNotSend exception in this function, the
     * application will not send the message. This is mostly useful if the
     * application has been asked to resend a message such as an order that is
     * no longer relevant for the current market. Messages that are being resent
     * are marked with the PossDupFlag in the header set to true; If a DoNotSend
     * exception is thrown and the flag is set to true, a sequence reset will be
     * sent in place of the message. If it is set to false, the message will
     * simply not be sent. You may add fields before an application message
     * before it is sent out.
     *
     * @param message   QuickFIX message
     * @param sessionId QuickFIX session ID
     * @throws DoNotSend This exception aborts message transmission
     */
    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        logger.info("To FIX Simulator = " + message);
    }

    /**
     * This callback receives messages for the application. This is one of the
     * core entry points for your FIX application. Every application level
     * request will come through here. If, for example, your application is a
     * sell-side OMS, this is where you will get your new order requests. If you
     * were a buy side, you would get your execution reports here. If a
     * FieldNotFound exception is thrown, the counterparty will receive a reject
     * indicating a conditionally required field is missing. The Message class
     * will throw this exception when trying to retrieve a missing field, so you
     * will rarely need the throw this explicitly. You can also throw an
     * UnsupportedMessageType exception. This will result in the counterparty
     * getting a business reject informing them your application cannot process
     * those types of messages. An IncorrectTagValue can also be thrown if a
     * field contains a value that is out of range or you do not support.
     *
     * @param message   QuickFIX message
     * @param sessionId QuickFIX session ID
     * @throws FieldNotFound
     * @throws IncorrectDataFormat
     * @throws IncorrectTagValue
     * @throws UnsupportedMessageType
     */
    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        logger.info("From FIX Simulator = " + message);
        try {
            super.addCustomFields(settings, message, sessionId);
        } catch (ConfigError configError) {
            logger.error("Error at From FIX Simulator: " + configError.getMessage(), configError);
        }
        crack(message, sessionId);
    }

    /**
     * This method will be called by MessageCracker whenever parameter matched.
     */
    @Override
    public void onMessage(Message message, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        final Message.Header header = message.getHeader();
        final String msgType = header.getString(MsgType.FIELD);
        if (isRejectableOrder(message, msgType)) {
            handleRejection(message, sessionID, msgType, header);
        } else if (isOrderMsg(msgType)) {
            handleAcceptedOrder(message, sessionID);
        } else if (msgType.charAt(0) == FixConstants.FIX_VALUE_35_USER_DEFINED) {
            UMessagerHandler.getUMessageHandler(sessionID).addMsg(message, sessionID);
        } else if (msgType.equals(TradingSessionStatusRequest.MSGTYPE)) {
            handleTradingSessionStatusReq(message, sessionID);
        } else if (msgType.equals(TradeCaptureReport.MSGTYPE)) {
            negDealExecutor = Executors.newFixedThreadPool(1);
            negDealExecutor.execute(new NegDealExecutor(message, sessionID));
        } else {
            logger.info("Simulator not implemented for: " + message.toString());
        }
    }

    private void handleTradingSessionStatusReq(Message message, SessionID sessionID) throws FieldNotFound {
        TradingSessionStatus tradingSessStatus = new TradingSessionStatus(new TradingSessionID(sessionID.getSenderCompID()), new TradSesStatus(TradSesStatus.OPEN));
        if (message.isSetField(TradingSessionID.FIELD)) {
            tradingSessStatus.setField(new StringField(MarketSegmentID.FIELD, message.getString(TradingSessionID.FIELD)));
            tradingSessStatus.setField(new IntField(TradingSessionSubID.FIELD, FixConstants.TAG625_TRADING));
            sendToTarget(tradingSessStatus, sessionID);
        }
    }

    private boolean isOrderMsg(String msgType) {
        return msgType.equals(NewOrderSingle.MSGTYPE)
                || msgType.equals(OrderCancelRequest.MSGTYPE)
                || msgType.equals(OrderCancelReplaceRequest.MSGTYPE);
    }

    private void handleAcceptedOrder(Message message, SessionID sessionID) throws FieldNotFound {
        OrderBean order = new OrderBean();
        order.setClOrdId(message.getString(ClOrdID.FIELD));
        order.setMessage(message);
        order.setSessionID(sessionID);
        if (message.isSetField(OrigClOrdID.FIELD)) {
            order.setOrigClOrdId(message.getString(OrigClOrdID.FIELD));
        }
        OrderHandler.getOrderHandler().addMsg(order);
    }

    private void handleRejection(Message message, SessionID sessionID, String msgType, Message.Header header) throws FieldNotFound {
        StringBuilder rejectReason = new StringBuilder("Simulator Rejection due to ");
        if (msgType.equals(NewOrderSingle.MSGTYPE)) {
            rejectReason.append("Quantity");
        } else {
            rejectReason.append("Status: ").append(header.getString(MsgType.FIELD));
        }
        sendToTarget(getRejectMessage(message, rejectReason.toString()), sessionID);
    }

    private boolean isRejectableOrder(Message message, String msgType) throws FieldNotFound {
        return (msgType.equals(NewOrderSingle.MSGTYPE)
                || msgType.equals(OrderCancelReplaceRequest.MSGTYPE))
                && message.getInt(OrderQty.FIELD) == Settings.getInt(SimulatorSettings.REJECT_QTY);
    }


    public void stopOrderHandler() {
        OrderHandler.stop();
    }

    public void stopNegDealExecutor() {
        if (negDealExecutor != null && !negDealExecutor.isShutdown()) {
            logger.info("Pool Shutdown : " + negDealExecutor.toString());
            negDealExecutor.shutdown();
        }
    }
}
