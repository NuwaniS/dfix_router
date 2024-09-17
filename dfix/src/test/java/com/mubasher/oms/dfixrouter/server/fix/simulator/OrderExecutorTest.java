package com.mubasher.oms.dfixrouter.server.fix.simulator;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SimulatorSettings;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.*;
import quickfix.fix42.*;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

class OrderExecutorTest {

    @Spy
    OrderExecutor orderExecutor;
    @Spy
    DFIXRouterManager dfixRouterManagerTest;
    MockedStatic<Settings> settingsMockedStatic ;
    MockedStatic<OrderHandler> orderHandlerMockedStatic ;
    MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic ;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        orderHandlerMockedStatic = Mockito.mockStatic(OrderHandler.class);
        dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);

        orderHandlerMockedStatic.when(OrderHandler::getOpenOrders).thenReturn(new ConcurrentHashMap<>());
        settingsMockedStatic.when(()->Settings.getString(SimulatorSettings.ORDER_EXECUTOR_COUNT)).thenReturn(IConstants.SETTING_NO);
        dfixRouterManagerMockedStatic.when(DFIXRouterManager::getInstance).thenReturn(dfixRouterManagerTest);
    }

    @AfterEach
    public void tearDown() {
        settingsMockedStatic.close();
        orderHandlerMockedStatic.close();
        dfixRouterManagerMockedStatic.close();
    }


    @Test
    void processMessage_failTest() throws Exception {
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Assertions.assertNull(reflectedMethod.invoke(orderExecutor, new Message()), "Null expected for dummy message invoke");
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
    }

    @Test
    void processMessage_flowTest() throws Exception {
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Assertions.assertNull(reflectedMethod.invoke(orderExecutor, new Logon()), "Null expected for dummy message invoke");
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
    }

    @Test
    void processMessage_exceptionTest() throws Exception {
        String clOrdId = "1";
        Message message = Mockito.mock(Message.class);
        Mockito.doReturn(clOrdId).when(orderExecutor).getClOrdId();
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Assertions.assertNull(reflectedMethod.invoke(orderExecutor, message));
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);

    }

    @Test
    void processNewOrder_failTest() throws Exception {
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Assertions.assertNull(reflectedMethod.invoke(orderExecutor, new NewOrderSingle()), "Null expected for dummy order invoke");
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
    }

    @Test
    void processNewOrder_passTest() throws Exception {
        String clOrdId = "1";
        NewOrderSingle order = new NewOrderSingle(new ClOrdID(clOrdId)
                , new HandlInst(HandlInst.MANUAL_ORDER_BEST_EXECUTION)
                , new Symbol("1010")
                , new Side(Side.BUY)
                , new TransactTime(LocalDateTime.now())
                , new OrdType(OrdType.MARKET));
        order.set(new OrderQty(IConstants.DEFAULT_FILL_QUANTITY * 2));
        order.set(new Account("1"));
        orderExecutor.setClOrdId(clOrdId);
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Message executionReport = (Message) reflectedMethod.invoke(orderExecutor, order);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        commonComparison(order, executionReport);
        Assertions.assertEquals(ExecTransType.NEW, executionReport.getChar(ExecTransType.FIELD), "ExecTransType has to be created as " + ExecTransType.NEW);
        Assertions.assertEquals(ExecType.NEW, executionReport.getChar(ExecType.FIELD), "ExecType has to be created as " + ExecType.NEW);
        Assertions.assertEquals(OrdStatus.NEW, executionReport.getChar(OrdStatus.FIELD), "OrdStatus has to be created as " + OrdStatus.NEW);
        Assertions.assertEquals(order.getDouble(OrderQty.FIELD), executionReport.getDouble(LeavesQty.FIELD), 0, "LeavesQty has to be order quantity.");
        Assertions.assertEquals(0, executionReport.getDouble(CumQty.FIELD), 0, "CumQty has to be 0.");
        Assertions.assertNotNull(OrderHandler.getOpenOrders().get(order.getString(ClOrdID.FIELD)), "ClOrdId has to be added to Open order map in Order handler");
        Assertions.assertEquals(executionReport, OrderHandler.getOpenOrders().get(order.getString(ClOrdID.FIELD)), "New Execution has to be updated to Open order map in Order handler");
        Assertions.assertDoesNotThrow(()->executionReport.getDouble(AvgPx.FIELD)>0, "AvgPx has to be created.");
        Assertions.assertDoesNotThrow(()->executionReport.getDouble(Price.FIELD), "Price has to be created.");
        Assertions.assertEquals(order.getDouble(OrderQty.FIELD), executionReport.getDouble(OrderQty.FIELD), 0, "OrderQty has to be equal.");
        Assertions.assertEquals(order.getDouble(OrdType.FIELD), executionReport.getDouble(OrdType.FIELD), 0, "OrdType has to be equal.");
        processExecution_passPartiallyFilledTest(executionReport);
    }

    private void commonComparison(Message before, Message after) throws FieldNotFound {
        Assertions.assertNotNull(after, "Non Null expected After invoke");
        Assertions.assertNotNull(after.getString(OrderID.FIELD), "OrderID has to be created.");
        Assertions.assertNotNull(after.getString(ExecID.FIELD), "ExecID has to be created.");
        Assertions.assertEquals(before.getString(Symbol.FIELD), after.getString(Symbol.FIELD), "Symbol has to be equal.");
        Assertions.assertEquals(before.getChar(Side.FIELD), after.getChar(Side.FIELD), "Side has to be equal.");
        Assertions.assertEquals(before.getString(ClOrdID.FIELD), after.getString(ClOrdID.FIELD), "ClOrdID has to be equal.");
        Assertions.assertEquals(before.getString(Account.FIELD), after.getString(Account.FIELD), "Account has to be equal.");
    }

    public void processExecution_passPartiallyFilledTest(Message accept) throws Exception {
        char status = ExecType.PARTIAL_FILL;
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Message executionReport = (Message) reflectedMethod.invoke(orderExecutor, accept);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);

        commonComparison(accept, executionReport);
        Assertions.assertEquals(ExecTransType.NEW, executionReport.getChar(ExecTransType.FIELD), "ExecTransType has to be created as " + ExecTransType.NEW);
        Assertions.assertEquals(status, executionReport.getChar(ExecType.FIELD), "ExecType has to be created as " + status);
        Assertions.assertEquals(status, executionReport.getChar(OrdStatus.FIELD), "OrdStatus has to be created as " + status);
        Assertions.assertEquals(executionReport.getInt(OrderQty.FIELD) - IConstants.DEFAULT_FILL_QUANTITY, executionReport.getDouble(LeavesQty.FIELD), 0, "LeavesQty has to be order remain Qty");
        Assertions.assertEquals(IConstants.DEFAULT_FILL_QUANTITY, executionReport.getDouble(CumQty.FIELD), 0, "CumQty has to be "+ IConstants.DEFAULT_FILL_QUANTITY);
        Assertions.assertNotNull(OrderHandler.getOpenOrders().get(accept.getString(ClOrdID.FIELD)), "ClOrdId has to be added to Open order map in Order handler");
        Assertions.assertEquals(executionReport, OrderHandler.getOpenOrders().get(accept.getString(ClOrdID.FIELD)), "New Execution has to be updated to Open order map in Order handler");
        Assertions.assertDoesNotThrow(()->executionReport.getDouble(AvgPx.FIELD)>0, "AvgPx has to be created.");
        Assertions.assertDoesNotThrow(()->executionReport.getDouble(Price.FIELD)>0, "Price has to be created.");
        Assertions.assertEquals(accept.getDouble(OrderQty.FIELD), executionReport.getDouble(OrderQty.FIELD), 0, "OrderQty has to be equal.");
        Assertions.assertEquals(accept.getDouble(OrdType.FIELD), executionReport.getDouble(OrdType.FIELD), 0, "OrdType has to be equal.");
        processExecution_passFilledTest(executionReport);
    }

    public void processExecution_passFilledTest(Message partiallyFilled) throws Exception {
        char status = ExecType.FILL;
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Message executionReport = (Message) reflectedMethod.invoke(orderExecutor, partiallyFilled);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);

        commonComparison(partiallyFilled, executionReport);
        Assertions.assertEquals(ExecTransType.NEW, executionReport.getChar(ExecTransType.FIELD), "ExecTransType has to be created as " + ExecTransType.NEW);
        Assertions.assertEquals(status, executionReport.getChar(ExecType.FIELD), "ExecType has to be created as " + status);
        Assertions.assertEquals(status, executionReport.getChar(OrdStatus.FIELD), "OrdStatus has to be created as " + status);
        Assertions.assertEquals(0, executionReport.getDouble(LeavesQty.FIELD), 0, "LeavesQty has to be 0.");
        Assertions.assertEquals(executionReport.getInt(OrderQty.FIELD), executionReport.getDouble(CumQty.FIELD), 0, "CumQty has to be Order quantity.");
        Assertions.assertFalse(OrderHandler.getOpenOrders().containsKey(executionReport.getString(ClOrdID.FIELD)), "ClOrdId has to be removed from map in Order handler");
        Assertions.assertDoesNotThrow(()->executionReport.getDouble(AvgPx.FIELD) > 0, "AvgPx has to be created.");
        Assertions.assertDoesNotThrow(()->executionReport.getDouble(Price.FIELD)>0, "Price has to be created.");
        Assertions.assertEquals(partiallyFilled.getDouble(OrderQty.FIELD), executionReport.getDouble(OrderQty.FIELD), 0, "OrderQty has to be equal.");
        Assertions.assertEquals(partiallyFilled.getDouble(OrdType.FIELD), executionReport.getDouble(OrdType.FIELD), 0, "OrdType has to be equal.");
    }

    @Test
    void processExecution_failTest() throws Exception {
        ExecutionReport order = new ExecutionReport();
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Message executionReport = (Message) reflectedMethod.invoke(orderExecutor, order);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);

        Assertions.assertNull(executionReport, "Null expected for dummy Execution Report invoke");
    }

    @Test
    void updateMarketPrice_passTest() throws Exception {
        NewOrderSingle initOrder = new NewOrderSingle(new ClOrdID("1")
                , new HandlInst(HandlInst.MANUAL_ORDER_BEST_EXECUTION)
                , new Symbol("1010")
                , new Side(Side.BUY)
                , new TransactTime(LocalDateTime.now())
                , new OrdType(OrdType.LIMIT));
        NewOrderSingle order = new NewOrderSingle(new ClOrdID("2")
                , new HandlInst(HandlInst.MANUAL_ORDER_BEST_EXECUTION)
                , new Symbol("1010")
                , new Side(Side.BUY)
                , new TransactTime(LocalDateTime.now())
                , new OrdType(OrdType.MARKET));
        initOrder.set(new Price(5));
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("updateMarketPrice",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        reflectedMethod.invoke(orderExecutor, initOrder);
        reflectedMethod.invoke(orderExecutor, order);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertNotNull(order.getPrice(), "Market order's price has to be set internally.");
        Assertions.assertEquals(initOrder.getPrice().getValue(), order.getPrice().getValue(), 0, "Limit order's price has to be stored and used to next Market order.");
    }

    @Test
    void processCancelRequest_failTest() throws Exception {
        OrderCancelRequest cancelRequest = new OrderCancelRequest();
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Message executionReport = (Message) reflectedMethod.invoke(orderExecutor, cancelRequest);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        Assertions.assertNull(executionReport, "Null expected for dummy Cancel Request invoke");
    }

    @Test
    void processCancelRequest_passTest() throws Exception {
        String clOrdId = "1";
        Message cancelRequest = new OrderCancelRequest();
        cancelRequest.setString(OrigClOrdID.FIELD, "0");
        cancelRequest.setString(ClOrdID.FIELD, clOrdId);
        cancelRequest.setString(Account.FIELD, "1");
        cancelRequest.setString(Symbol.FIELD, "1010");
        cancelRequest.setChar(Side.FIELD, Side.BUY);
        cancelRequest.setDouble(CumQty.FIELD, 100);
        Mockito.when(orderExecutor.getClOrdId()).thenReturn(clOrdId);
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Message executionReport = (Message) reflectedMethod.invoke(orderExecutor, cancelRequest);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        commonComparison(cancelRequest, executionReport);
    }

    @Test
    void processReplaceRequest_failTest() throws Exception {
        OrderCancelReplaceRequest replaceRequest = new OrderCancelReplaceRequest();
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Message executionReport = (Message) reflectedMethod.invoke(orderExecutor, replaceRequest);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);

        Assertions.assertNull(executionReport, "Null expected for dummy Replace Request invoke");
    }

    @Test
    void processReplaceRequest_passTest() throws Exception {
        String clOrdId = "1";
        OrderCancelReplaceRequest replaceRequest = new OrderCancelReplaceRequest();
        replaceRequest.set(new ClOrdID(clOrdId));
        replaceRequest.set(new Symbol("1010"));
        replaceRequest.set(new Side(Side.BUY));
        replaceRequest.set(new OrderQty(2 * IConstants.DEFAULT_FILL_QUANTITY));
        replaceRequest.setDouble(CumQty.FIELD, IConstants.DEFAULT_FILL_QUANTITY);
        replaceRequest.set(new Price(10));
        replaceRequest.set(new OrdType(OrdType.LIMIT));
        replaceRequest.set(new OrigClOrdID("0"));
        replaceRequest.set(new Account("1"));
        orderExecutor.setClOrdId(clOrdId);
        Method reflectedMethod = orderExecutor.getClass().getDeclaredMethod("processMessage",Message.class);
        reflectedMethod.setAccessible(IConstants.CONSTANT_TRUE);
        Message executionReport = (Message) reflectedMethod.invoke(orderExecutor, replaceRequest);
        reflectedMethod.setAccessible(IConstants.CONSTANT_FALSE);
        commonComparison(replaceRequest, executionReport);
        Assertions.assertEquals(IConstants.DEFAULT_FILL_QUANTITY, executionReport.getInt(LeavesQty.FIELD), 0, "Leaves Qty has to be calculated as difference between OrdQty and CumQty.");
    }

    @Test
    void isRunning_flowTest() {
        Assertions.assertFalse(orderExecutor.isRunning(), "Default value should be false.");
    }

    @Test
    void run_flowTest() {
        Mockito.doReturn(true).when(dfixRouterManagerTest).isStarted();
        orderExecutor.run();//isRunning false coverage
        Mockito.doReturn(true).when(orderExecutor).isRunning();
        orderExecutor.run();//getClOrdId null coverage
        String clOrdId = "1";
        OrderHandler.getOpenOrders().put(clOrdId, new Logon());
        orderExecutor.setClOrdId(clOrdId);
        orderExecutor.run();
    }
}
