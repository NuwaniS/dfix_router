package com.mubasher.oms.dfixrouter.server.fix.flowcontrol;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.field.*;
import quickfix.fix42.ExecutionReport;
import quickfix.fix42.NewOrderSingle;
import quickfix.fix42.OrderCancelReplaceRequest;

/**
 * Created by Nilaan L on 8/1/2024.
 */
class FlowControllerTest {

    FlowController flowController;
    Message message;

    @BeforeEach
    void setUp() {
        flowController = new FlowController(IConstants.CONSTANT_TEN_10);
        message = new Message();
        message.setString(Symbol.FIELD, "1010");
        message.setChar(Side.FIELD, '1');
        message.setDouble(Price.FIELD, 10.2);
        message.setDouble(OrderQty.FIELD, IConstants.CONSTANT_TEN_10);
    }

    @Test
    void isSendMessage_Duplicate_Test() {
        //duplicateMessageWindowLimit = 1
        message.getHeader().setString(MsgType.FIELD, ExecutionReport.MSGTYPE);
        flowController.setDuplicateMessageWindowLimit(IConstants.CONSTANT_ONE_1);
        final FlowControlStatus status1 = flowController.isSendMessage(message, IConstants.CONSTANT_TRUE); //place first
        Assertions.assertEquals(FlowControlStatus.ALLOWED, status1);
        final FlowControlStatus status2 = flowController.isSendMessage(message, IConstants.CONSTANT_TRUE); //place duplicate
        Assertions.assertEquals(FlowControlStatus.DUPLICATE_SUSTAINED_BLOCKED, status2);
    }

    @Test
    void isSendMessage_NewOrderDuplicate_Test() {
        //NewMessageWindowLimit = 1
        message.getHeader().setString(MsgType.FIELD, NewOrderSingle.MSGTYPE);
        flowController.setNewMessageWindowLimit(IConstants.CONSTANT_TWO_2);
        final FlowControlStatus status1 = flowController.isSendMessage(message, IConstants.CONSTANT_TRUE); //place first
        Assertions.assertEquals(FlowControlStatus.ALLOWED, status1);
        final FlowControlStatus status2 = flowController.isSendMessage(message, IConstants.CONSTANT_TRUE); //place second ;
        Assertions.assertEquals(FlowControlStatus.NEW_ORDER_SUSTAINED_BLOCKED, status2);
        final FlowControlStatus status3 = flowController.isSendMessage(message, IConstants.CONSTANT_TRUE); //place third ; already newMessageWindowLimit reached
        Assertions.assertEquals(FlowControlStatus.NEW_ORDER_SUSTAINED_BLOCKED, status3);
    }

    @Test
    void isSendMessage_OrderReplaceDuplicate_Test() {
        //NewMessageWindowLimit = 1
        message.getHeader().setString(MsgType.FIELD, OrderCancelReplaceRequest.MSGTYPE);
        flowController.setAmendMessageWindowLimit(IConstants.CONSTANT_TWO_2);
        final FlowControlStatus status1 = flowController.isSendMessage(message, IConstants.CONSTANT_TRUE); //place first
        Assertions.assertEquals(FlowControlStatus.ALLOWED, status1);
        final FlowControlStatus status2 = flowController.isSendMessage(message, IConstants.CONSTANT_TRUE); //place second ;
        Assertions.assertEquals(FlowControlStatus.AMEND_ORDER_SUSTAINED_BLOCKED, status2);
        final FlowControlStatus status3 = flowController.isSendMessage(message, IConstants.CONSTANT_TRUE); //place third ; already newMessageWindowLimit reached
        Assertions.assertEquals(FlowControlStatus.AMEND_ORDER_SUSTAINED_BLOCKED, status3);
    }

    @Test
    void checkDuplicateByCliOrdId_Test() throws Exception {
        message.getHeader().setString(MsgType.FIELD, NewOrderSingle.MSGTYPE);
        message.setString(ClOrdID.FIELD, "123");
        final FlowControlStatus status = (FlowControlStatus) TestUtils.invokePrivateMethod(flowController, "checkDuplicateMessageByCliOrdId", new Class[]{Message.class, boolean.class}, message, IConstants.CONSTANT_FALSE);
        Assertions.assertEquals(FlowControlStatus.ALLOWED, status);
        final FlowControlStatus status1 = (FlowControlStatus) TestUtils.invokePrivateMethod(flowController, "checkDuplicateMessageByCliOrdId", new Class[]{Message.class, boolean.class}, message, IConstants.CONSTANT_FALSE);
        Assertions.assertEquals(FlowControlStatus.CLIORDID_DUPLICATE_BLOCKED, status1);
    }
}
