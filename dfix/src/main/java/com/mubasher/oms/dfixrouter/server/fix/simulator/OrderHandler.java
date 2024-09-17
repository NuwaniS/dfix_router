package com.mubasher.oms.dfixrouter.server.fix.simulator;

import com.mubasher.oms.dfixrouter.beans.OrderBean;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SimulatorSettings;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.system.Settings;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.AvgPx;
import quickfix.field.CumQty;
import quickfix.field.OrderQty;
import quickfix.field.Price;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OrderHandler {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.simulator.OrderHandler");
    private static final int ORDER_EXECUTORS_COUNT = Settings.getInt(SimulatorSettings.ORDER_EXECUTOR_COUNT) > 0 ? Settings.getInt(SimulatorSettings.ORDER_EXECUTOR_COUNT) : IConstants.DEFAULT_ORDER_HANDLER_COUNT;
    private static final HashMap<String, OrderExecutor> HANDLED_ORDERS = new HashMap<>();
    private static final ConcurrentMap<String, Message> OPEN_ORDERS = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTORSERVICE = Executors.newFixedThreadPool(ORDER_EXECUTORS_COUNT);
    private static OrderHandler orderHandler;

    private OrderHandler() {
        super();
    }

    public static synchronized OrderHandler getOrderHandler() {
        if (orderHandler == null) {
            orderHandler = new OrderHandler();
            logger.info("Order Handlers started: " + ORDER_EXECUTORS_COUNT);
        }
        return orderHandler;
    }

    public static void stop() {
        EXECUTORSERVICE.shutdown();
    }

    public void addMsg(OrderBean orderBean) {
        OPEN_ORDERS.put(orderBean.getClOrdId(), orderBean.getMessage());
        checkChangeOrder(orderBean);
        OrderExecutor orderExecutor = new OrderExecutor();
        orderExecutor.setClOrdId(orderBean.getClOrdId());
        orderExecutor.setSessionID(orderBean.getSessionID());
        orderExecutor.setRunning(true);
        HANDLED_ORDERS.put(orderBean.getClOrdId(), orderExecutor);
        EXECUTORSERVICE.execute(orderExecutor);
    }

    private void checkChangeOrder(OrderBean orderBean) {
        if (orderBean.getOrigClOrdId() != null
                && HANDLED_ORDERS.containsKey(orderBean.getOrigClOrdId())
                && OPEN_ORDERS.get(orderBean.getOrigClOrdId()) != null) {
            OrderExecutor orderExecutor = HANDLED_ORDERS.get(orderBean.getOrigClOrdId());
            orderExecutor.setRunning(false);
            updateNewOrderData(OPEN_ORDERS.get(orderBean.getOrigClOrdId()), orderBean.getMessage());
        }
    }

    private void updateNewOrderData(Message origOrderMessage, Message newOrderMessage) {
        try {
            newOrderMessage.setDouble(CumQty.FIELD, origOrderMessage.getDouble(CumQty.FIELD));
            if (!newOrderMessage.isSetField(Price.FIELD)) {
                newOrderMessage.setDouble(Price.FIELD, origOrderMessage.getDouble(Price.FIELD));
            }
            if (!newOrderMessage.isSetField(AvgPx.FIELD)) {
                double avgPrice = ((origOrderMessage.getDouble(CumQty.FIELD) * origOrderMessage.getDouble(Price.FIELD))
                        + (newOrderMessage.getDouble(Price.FIELD) * (newOrderMessage.getDouble(OrderQty.FIELD) - origOrderMessage.getDouble(CumQty.FIELD)))) / newOrderMessage.getDouble(OrderQty.FIELD);
                newOrderMessage.setDouble(AvgPx.FIELD, avgPrice);
            }
        } catch (FieldNotFound fieldNotFound) {
            logger.error("Error at updateNewOrderData: " + fieldNotFound.getMessage(), fieldNotFound);
        }
    }

    public static ConcurrentMap<String, Message> getOpenOrders() {
        return OPEN_ORDERS;
    }
}
