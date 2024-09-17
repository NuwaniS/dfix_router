package com.mubasher.oms.dfixrouter.server.fix.simulator;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SimulatorSettings;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.fix.FIXApplicationCommonLogic;
import com.mubasher.oms.dfixrouter.system.Settings;
import quickfix.FieldNotFound;
import quickfix.Group;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.*;
import quickfix.fix42.ExecutionReport;
import quickfix.fix42.NewOrderSingle;
import quickfix.fix42.OrderCancelReplaceRequest;
import quickfix.fix42.OrderCancelRequest;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;

public class OrderExecutor extends FIXApplicationCommonLogic implements Runnable {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.simulator.OrderExecutor");
    private static final int FILL_QTY = Settings.getInt(SimulatorSettings.FILL_QTY) > 0 ? Settings.getInt(SimulatorSettings.FILL_QTY) : IConstants.DEFAULT_FILL_QUANTITY;
    private static final int DELAY = Settings.getInt(SimulatorSettings.EXECUTION_INTERVAL) > 0 ? Settings.getInt(SimulatorSettings.EXECUTION_INTERVAL) : 0;//Default delay is 0
    private static final HashMap<String, Double> MARKET_PRICE = new HashMap<>();
    private String clOrdId;
    private boolean isRunning = false;
    private SessionID sessionID;

    public OrderExecutor() {
        super();
    }

    private static void updateMarketPrice(Message order) throws FieldNotFound, NoSuchAlgorithmException {
        if (!order.isSetField(Price.FIELD)) {
            if (MARKET_PRICE.containsKey(order.getString(Symbol.FIELD))) {
                order.setDouble(Price.FIELD, MARKET_PRICE.get(order.getString(Symbol.FIELD)));
            } else {
                Double price = SecureRandom.getInstanceStrong().nextDouble() * 100;
                MARKET_PRICE.put(order.getString(Symbol.FIELD), price);
                order.setDouble(Price.FIELD, price);
            }
        } else {
            MARKET_PRICE.put(order.getString(Symbol.FIELD), order.getDouble(Price.FIELD));
        }
    }

    public String getClOrdId() {
        return clOrdId;
    }

    public void setClOrdId(String clOrdId) {
        this.clOrdId = clOrdId;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public SessionID getSessionID() {
        return sessionID;
    }

    public void setSessionID(SessionID sessionID) {
        this.sessionID = sessionID;
    }

    @Override
    public void run() {
        while (DFIXRouterManager.getInstance().isStarted()) {
            if (isRunning()
                    && getClOrdId() != null
                    && OrderHandler.getOpenOrders().get(getClOrdId()) != null) {
                Message message = OrderHandler.getOpenOrders().get(getClOrdId());
                processMessage(message);
                if (DELAY > 0) {
                    DFIXRouterManager.sleepThread(DELAY);
                }
            } else if (getClOrdId() != null && !OrderHandler.getOpenOrders().containsKey(getClOrdId())) {
                reInitiateSelf();
            } else {
                break;
            }
        }
    }

    private Message processCancelRequest(Message cancelRequest) {
        Message cancelExecution = new Message();
        try {
            OrderHandler.getOpenOrders().remove(cancelRequest.getString(OrigClOrdID.FIELD));
            cancelExecution.getHeader().setString(MsgType.FIELD, ExecutionReport.MSGTYPE);
            cancelExecution.setString(ClOrdID.FIELD, cancelRequest.getString(ClOrdID.FIELD));
            cancelExecution.setString(OrigClOrdID.FIELD, cancelRequest.getString(OrigClOrdID.FIELD));
            cancelExecution.setString(OrderID.FIELD, cancelRequest.getString(OrigClOrdID.FIELD));
            cancelExecution.setString(Account.FIELD, cancelRequest.getString(Account.FIELD));
            cancelExecution.setString(Symbol.FIELD, cancelRequest.getString(Symbol.FIELD));
            cancelExecution.setChar(Side.FIELD, cancelRequest.getChar(Side.FIELD));
            cancelExecution.setString(ExecID.FIELD, FIXApplicationCommonLogic.getExecutionId());
            cancelExecution.setUtcTimeStamp(TransactTime.FIELD, LocalDateTime.now());
            cancelExecution.setChar(ExecType.FIELD, ExecType.CANCELED);
            cancelExecution.setChar(OrdStatus.FIELD, OrdStatus.CANCELED);
            if (cancelRequest.isSetField(CumQty.FIELD)) {
                cancelExecution.setDouble(CumQty.FIELD, cancelRequest.getDouble(CumQty.FIELD));
            }
            if (cancelRequest.getHeader().isSetField(OnBehalfOfCompID.FIELD)){
                cancelExecution.getHeader().setString(DeliverToCompID.FIELD, cancelRequest.getHeader().getString(OnBehalfOfCompID.FIELD));
            } else if (cancelRequest.getHeader().isSetField(OnBehalfOfSubID.FIELD)){
                cancelExecution.getHeader().setString(DeliverToCompID.FIELD, cancelRequest.getHeader().getString(OnBehalfOfSubID.FIELD));
            }
            logger.debug(cancelExecution.toString());
            sendToTarget(cancelExecution, getSessionID());
            reInitiateSelf();
        } catch (FieldNotFound fieldNotFound) {
            logger.error("OrderExecutor.cancelExecution: " + fieldNotFound.getMessage(), fieldNotFound);
            cancelExecution = null;
        }
        return cancelExecution;
    }

    private Message processMessage(Message message) {
        Message result = null;
        try {
            if (message.getHeader().isSetField(MsgType.FIELD)){
                switch (message.getHeader().getString(MsgType.FIELD)){
                    case NewOrderSingle.MSGTYPE:
                        result = processNewOrder(message);
                    break;
                    case OrderCancelRequest.MSGTYPE:
                        result = processCancelRequest(message);
                    break;
                    case OrderCancelReplaceRequest.MSGTYPE:
                        result = processReplaceRequest(message);
                    break;
                    case ExecutionReport.MSGTYPE:
                        result = processExecution(message);
                    break;
                    default:
                        logger.info("OrderExecutor not implemented for: " + message.toString());
                        if (getClOrdId() != null) {
                            reInitiateSelf();
                        }
                        break;

                }
            }
        } catch (Exception ex) {
            logger.error("OrderExecutor.processMessage: " + ex.getMessage(), ex);
            reInitiateSelf();
        }
        return result;
    }

    private Message processNewOrder(Message order) {
        Message accept = null;
        try {
            updateMarketPrice(order);
            accept = new Message();
            accept.getHeader().setString(MsgType.FIELD, ExecutionReport.MSGTYPE);
            accept.setString(OrderID.FIELD, order.getString(ClOrdID.FIELD));
            accept.setString(ExecID.FIELD, FIXApplicationCommonLogic.getExecutionId());
            accept.setChar(ExecTransType.FIELD, ExecTransType.NEW);
            accept.setChar(ExecType.FIELD, ExecType.NEW);
            accept.setChar(OrdStatus.FIELD, OrdStatus.NEW);
            accept.setString(Symbol.FIELD, order.getString(Symbol.FIELD));
            accept.setChar(Side.FIELD, order.getChar(Side.FIELD));
            accept.setDouble(LeavesQty.FIELD, order.getDouble(OrderQty.FIELD));
            accept.setDouble(CumQty.FIELD, 0);
            accept.setDouble(AvgPx.FIELD, order.getDouble(Price.FIELD));
            accept.setDouble(Price.FIELD, order.getDouble(Price.FIELD));
            accept.setDouble(OrderQty.FIELD, order.getDouble(OrderQty.FIELD));
            accept.setChar(OrdType.FIELD, order.getChar(OrdType.FIELD));
            accept.setString(ClOrdID.FIELD, order.getString(ClOrdID.FIELD));
            accept.setString(Account.FIELD, order.getString(Account.FIELD));
            if (order.hasGroup(NoPartyIDs.FIELD)) {
                populatePTTPTags(order, accept);
            }
            accept.setUtcTimeStamp(TransactTime.FIELD, LocalDateTime.now());
            if (order.getHeader().isSetField(OnBehalfOfCompID.FIELD)){
                accept.getHeader().setString(DeliverToCompID.FIELD, order.getHeader().getString(OnBehalfOfCompID.FIELD));
            } else if (accept.getHeader().isSetField(OnBehalfOfSubID.FIELD)){
                accept.getHeader().setString(DeliverToCompID.FIELD, accept.getHeader().getString(OnBehalfOfSubID.FIELD));
            }
            logger.debug(accept.toString());
            if (IConstants.SETTING_YES.equals(Settings.getString(SimulatorSettings.IS_QUEUING_ONLY))) {
                reInitiateSelf();
            } else {
                OrderHandler.getOpenOrders().put(getClOrdId(), accept);
            }
            sendToTarget(accept, getSessionID());
        } catch (Exception ex) {
            logger.error("OrderExecutor.sendOrderAcceptance: " + ex.getMessage(), ex);
        }
        return accept;
    }

    private void populatePTTPTags(Message order, Message executionReport) {
        for (int i = 1; i <= 4; i++) {
            Group group = new Group(NoPartyIDs.FIELD, PartyID.FIELD);
            group.setString(PartyID.FIELD, "DFIXSimulator : PARTY" + i);
            group.setChar(PartyIDSource.FIELD, PartyIDSource.GENERALLY_ACCEPTED_MARKET_PARTICIPANT_IDENTIFIER);
            switch (i){
                case 1:
                    group.setInt(PartyRole.FIELD, PartyRole.ORDER_ORIGINATION_TRADER);
                    break;
                case 2:
                    group.setInt(PartyRole.FIELD, PartyRole.ENTERING_TRADER);
                    break;
                case 3:
                    group.setInt(PartyRole.FIELD, PartyRole.EXECUTING_TRADER);
                    break;
                case 4:
                    group.setInt(PartyRole.FIELD, PartyRole.EXECUTING_FIRM);
                    break;
                default:
                    //Outer for loop limits values allowed for switch to 1-4
            }
            executionReport.addGroup(group);
        }
        for (Group group : order.getGroups(NoPartyIDs.FIELD)) {
            executionReport.addGroup(group);
        }
    }

    private Message processExecution(Message executionReport) {
        Message execution = (Message) executionReport.clone();
        double qty;
        char status;
        try {
            double ordQty = executionReport.getDouble(OrderQty.FIELD);
            double cumQty = executionReport.getDouble(CumQty.FIELD);
            if ((ordQty - cumQty) > FILL_QTY) {
                qty = FILL_QTY;
                status = ExecType.PARTIAL_FILL;
            } else {
                qty = ordQty - cumQty;
                status = ExecType.FILL;
            }
            cumQty += qty;
            execution.setString(ExecID.FIELD, FIXApplicationCommonLogic.getExecutionId());
            execution.setChar(ExecType.FIELD, status);
            execution.setChar(OrdStatus.FIELD, status);
            execution.setUtcTimeStamp(TransactTime.FIELD, LocalDateTime.now());
            execution.setDouble(LeavesQty.FIELD, ordQty - cumQty);
            execution.setDouble(CumQty.FIELD, cumQty);
            execution.setDouble(LastShares.FIELD, qty);
            execution.setString(ClientID.FIELD, "1");
            execution.setDouble(LastPx.FIELD, executionReport.getDouble(Price.FIELD));
            logger.debug(execution.toString());
            OrderHandler.getOpenOrders().put(getClOrdId(), execution);
            sendToTarget(execution, getSessionID());
            if (status == ExecType.FILL) {
                reInitiateSelf();
            }
        } catch (FieldNotFound fieldNotFound) {
            logger.error("OrderExecutor.sendOrderExecution: " + fieldNotFound.getMessage(), fieldNotFound);
            execution = null;
        }
        return execution;
    }

    private void reInitiateSelf() {
        OrderHandler.getOpenOrders().remove(getClOrdId());
        setClOrdId(null);
        setRunning(false);
    }

    private Message processReplaceRequest(Message replaceRequest) {
        Message replace = new Message();
        try {
            replace.getHeader().setString(MsgType.FIELD, ExecutionReport.MSGTYPE);
            replace.setString(OrderID.FIELD, replaceRequest.getString(ClOrdID.FIELD));
            replace.setString(ExecID.FIELD, FIXApplicationCommonLogic.getExecutionId());
            replace.setChar(ExecTransType.FIELD, ExecTransType.NEW);
            replace.setChar(ExecType.FIELD, ExecType.REPLACED);
            replace.setChar(OrdStatus.FIELD, OrdStatus.NEW);
            replace.setString(Symbol.FIELD, replaceRequest.getString(Symbol.FIELD));
            replace.setChar(Side.FIELD, replaceRequest.getChar(Side.FIELD));
            replace.setDouble(LeavesQty.FIELD, replaceRequest.getDouble(OrderQty.FIELD) - replaceRequest.getDouble(CumQty.FIELD));
            replace.setDouble(CumQty.FIELD, replaceRequest.getDouble(CumQty.FIELD));
            replace.setDouble(AvgPx.FIELD, replaceRequest.getDouble(Price.FIELD));
            replace.setDouble(Price.FIELD, replaceRequest.getDouble(Price.FIELD));
            replace.setDouble(OrderQty.FIELD, replaceRequest.getDouble(OrderQty.FIELD));
            replace.setChar(OrdType.FIELD, replaceRequest.getChar(OrdType.FIELD));
            replace.setString(ClOrdID.FIELD, replaceRequest.getString(ClOrdID.FIELD));
            replace.setString(OrigClOrdID.FIELD, replaceRequest.getString(OrigClOrdID.FIELD));
            replace.setString(Account.FIELD, replaceRequest.getString(Account.FIELD));
            if (replaceRequest.getHeader().isSetField(OnBehalfOfCompID.FIELD)){
                replace.getHeader().setString(DeliverToCompID.FIELD, replaceRequest.getHeader().getString(OnBehalfOfCompID.FIELD));
            } else if (replace.getHeader().isSetField(OnBehalfOfSubID.FIELD)){
                replace.getHeader().setString(DeliverToCompID.FIELD, replace.getHeader().getString(OnBehalfOfSubID.FIELD));
            }
            replace.setUtcTimeStamp(TransactTime.FIELD, LocalDateTime.now());
            logger.debug(replace.toString());
            OrderHandler.getOpenOrders().put(getClOrdId(), replace);
            sendToTarget(replace, getSessionID());
        } catch (FieldNotFound fieldNotFound) {
            logger.error("OrderExecutor.sendOrderAcceptance: " + fieldNotFound.getMessage(), fieldNotFound);
            replace = null;
        }
        return replace;
    }
}
