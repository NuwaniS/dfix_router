package com.mubasher.oms.dfixrouter.server.fix.simulator;

import com.mubasher.oms.dfixrouter.constants.FixConstants;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SimulatorSettings;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.TestUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import quickfix.Group;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.field.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

/**
 * Created by Nilaan L on 7/22/2024.
 */
class UMessagerHandlerTest {

    MockedStatic<Session> sessionMockedStatic;
    MockedStatic<UMessagerHandler> umMessageHandlerMockedStatic;
    MockedStatic<Settings> settingsMockedStatic;
    @Spy
    UMessagerHandler umMessageHandlerSpy;

    @Spy
    Message message;
    SessionID sessionId;
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        settingsMockedStatic = Mockito.mockStatic(Settings.class);
        sessionMockedStatic = Mockito.mockStatic(Session.class);
        umMessageHandlerMockedStatic = Mockito.mockStatic(UMessagerHandler.class);
        settingsMockedStatic.when(()->Settings.getInt(SimulatorSettings.REJECT_QTY)).thenReturn(IConstants.CONSTANT_ONE_1);
        settingsMockedStatic.when(()->Settings.getInt(SimulatorSettings.U_REJECT_PROPABILITY)).thenReturn(IConstants.CONSTANT_FIVE_5);
        umMessageHandlerMockedStatic.when(()->UMessagerHandler.getUMessageHandler(any(SessionID.class))).thenReturn(umMessageHandlerSpy);
        sessionMockedStatic.when(()->Session.sendToTarget(any(Message.class),any(SessionID.class))).thenReturn(IConstants.CONSTANT_TRUE);

        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_USER_DEFINED+"1");
        populateUResponseFields(message);
        sessionId = new SessionID("FIX.4.2","SLGM0","TDWL");
    }

    @AfterEach
    void tearDown() {
        sessionMockedStatic.close();
        if (!umMessageHandlerMockedStatic.isClosed()){
            umMessageHandlerMockedStatic.close();
        }
        settingsMockedStatic.close();
    }

    @Test
    void getUMessageHandler_Test() {
        umMessageHandlerMockedStatic.close(); //disable static mocking to check real behaviour of  getUMessageHandler(..)
        final UMessagerHandler uMessageHandler1 = UMessagerHandler.getUMessageHandler(sessionId);
        final UMessagerHandler uMessageHandler2 = UMessagerHandler.getUMessageHandler(sessionId);
        assertEquals(uMessageHandler1, uMessageHandler2);
    }

    @Test
    void addMsg_Test() {
        umMessageHandlerSpy.addMsg(message,sessionId);
    }

    @Test
    void populateUResponse_Test() throws Exception {
        final Message uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "populateUResponse", new Class[]{Message.class}, message);
        Assertions.assertEquals("TDWL",uResponse.getHeader().getString(SenderCompID.FIELD));
        Assertions.assertEquals("SLGM0",uResponse.getHeader().getString(TargetCompID.FIELD));
        Assertions.assertEquals("TDWLSUB",uResponse.getHeader().getString(SenderSubID.FIELD));
        Assertions.assertEquals("SLGM0SUB",uResponse.getHeader().getString(TargetSubID.FIELD));
        Assertions.assertEquals("9742",uResponse.getString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID));
    }

    private void populateUResponseFields(Message message) {
        message.getHeader().setString(TargetCompID.FIELD, "TDWL");
        message.getHeader().setString(SenderCompID.FIELD, "SLGM0");
        message.getHeader().setString(SenderSubID.FIELD, "SLGM0SUB");
        message.getHeader().setString(TargetSubID.FIELD, "TDWLSUB");
        message.setString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID, "9742");
    }

    @ParameterizedTest
    @ValueSource(ints = {1,2,3,4,5,6,7,8,9,10,11,12,13})
    void populateUReject_Test(int refReqNo) throws Exception {
        TestUtils.invokePrivateMethod(umMessageHandlerSpy, "populateUReject", new Class[]{Message.class,int.class}, message,refReqNo);
        int rejectionNumber = (refReqNo % 7) + 1;
        assertEquals(String.valueOf(rejectionNumber), message.getString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE));
        assertEquals(refReqNo, message.getInt(RefSeqNum.FIELD));
        assertEquals(String.valueOf(rejectionNumber),  message.getString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE));

        switch (rejectionNumber){
            case 1:
                assertEquals("Unknown ID", message.getString(Text.FIELD));
                break;
            case 2:
                assertEquals("Unknown Security", message.getString(Text.FIELD));
                break;
            case 3:
                assertEquals("Unknown Message Type", message.getString(Text.FIELD));
                break;
            case 4:
                assertEquals("Application not available", message.getString(Text.FIELD));
                break;
            case 5:
                assertEquals("Conditionally required field missing", message.getString(Text.FIELD));
                break;
            case 6:
                assertEquals("Not Authorized", message.getString(Text.FIELD));
                break;
            case 7:
                assertEquals("Subscription not exists", message.getString(Text.FIELD));
                break;
            default:
                assertFalse(message.isSetField(Text.FIELD));
                break;
        }

    }

    @Test
    void getResponseMessage_U10_Test() throws Exception {
        Message uResponse;

        //U10 CREATE_ACCOUNT_REQUEST
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_CREATE_ACCOUNT_REQUEST);
        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);

        assertEquals(FixConstants.FIX_VALUE_35_CREATE_ACCOUNT_RESPONSE, uResponse.getHeader().getString(MsgType.FIELD));
        assertTrue(uResponse.isSetField(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
        assertEquals(IConstants.STRING_2, uResponse.getString(FixConstants.FIX_TAG_CREATE_CODE));
        assertEquals("DFIXSimulator : ACCOUNT_OWNER_NAME", uResponse.getString(FixConstants.FIX_TAG_ACCOUNT_OWNER_NAME));
    }

    @Test
    void getResponseMessage_U12_Test() throws Exception {
        Message uResponse;
        //U12 VIEW_ACCOUNT_REQUEST
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_VIEW_ACCOUNT_REQUEST);
        message.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, IConstants.STRING_1);
        message.setString(FixConstants.FIX_TAG_MEMBER_CODE, IConstants.STRING_11);
        message.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, "1122");

        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);

        assertEquals(FixConstants.FIX_VALUE_35_VIEW_ACCOUNT_RESPONSE,uResponse.getHeader().getString(MsgType.FIELD));
        assertEquals(IConstants.CONSTANT_FIVE_5, uResponse.getInt(FixConstants.FIX_TAG_9710_COUNT));
        final List<Group> group9710 = uResponse.getGroups(FixConstants.FIX_TAG_9710_COUNT);
        assertEquals(IConstants.CONSTANT_FIVE_5, group9710.size());
        assertEquals(IConstants.CONSTANT_FIVE_5, uResponse.getInt(FixConstants.FIX_TAG_100000_DUMMY_COUNT));
        final List<Group> group100000 = uResponse.getGroups(FixConstants.FIX_TAG_9710_COUNT);
        assertEquals(IConstants.CONSTANT_FIVE_5, group100000.size());
        assertTrue(uResponse.isSetField(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
        assertEquals("DFIXSimulator : ACCOUNT_OWNER_NAME", uResponse.getString(FixConstants.FIX_TAG_ACCOUNT_OWNER_NAME));

    }

    @Test
    void getResponseMessage_U16_Test() throws Exception {
        Message uResponse;
        //U16 DISABLE_ACCOUNT_REQUEST
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_DISABLE_ACCOUNT_REQUEST);
        message.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, IConstants.STRING_1);
        message.setString(FixConstants.FIX_TAG_MEMBER_CODE, IConstants.STRING_11);
        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);
        assertEquals(FixConstants.FIX_VALUE_35_DISABLE_ACCOUNT_RESPONSE,uResponse.getHeader().getString(MsgType.FIELD));

        assertEquals(IConstants.STRING_0, uResponse.getString(FixConstants.FIX_TAG_DELETE_RETURN_CODE));
        assertEquals(IConstants.STRING_11,uResponse.getString(FixConstants.FIX_TAG_MEMBER_CODE));
        assertEquals(IConstants.STRING_1,uResponse.getString(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
    }

    @Test
    void getResponseMessage_U18_Test() throws Exception {
        Message uResponse;
        //U18 PORTFOLIO_INQUIRY_REQUEST
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_PORTFOLIO_INQUIRY_REQUEST);
        message.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, IConstants.STRING_1);

        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);
        assertEquals(FixConstants.FIX_VALUE_35_PORTFOLIO_INQUIRY_RESPONSE,uResponse.getHeader().getString(MsgType.FIELD));
        assertEquals(IConstants.STRING_1,uResponse.getString(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
        assertEquals(IConstants.STRING_0, uResponse.getString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE));
        assertEquals(IConstants.CONSTANT_FIVE_5, uResponse.getInt(NoRelatedSym.FIELD));
        final List<Group> group146 = uResponse.getGroups(NoRelatedSym.FIELD);
        assertEquals(IConstants.CONSTANT_FIVE_5, group146.size());
    }

    @Test
    void getResponseMessage_U20_Test() throws Exception {
        Message uResponse;
        //U20 PLEDGE_UNPLEDGE_REQUEST
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_PLEDGE_UNPLEDGE_REQUEST);
        message.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, IConstants.STRING_1);
        message.setString(FixConstants.FIX_TAG_REFERENCE, IConstants.STRING_11);
        message.setString(FixConstants.FIX_TAG_MEMBER_CODE, IConstants.STRING_11);

        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);
        assertEquals(FixConstants.FIX_VALUE_35_PLEDGE_UNPLEDGE_RESPONSE,uResponse.getHeader().getString(MsgType.FIELD));
        assertEquals(IConstants.STRING_0, uResponse.getString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE));
        assertEquals(IConstants.STRING_11, uResponse.getString(FixConstants.FIX_TAG_REFERENCE));
        assertEquals(IConstants.STRING_1,uResponse.getString(FixConstants.FIX_TAG_ACCOUNT_NUMBER));
        assertEquals(IConstants.STRING_11,uResponse.getString(FixConstants.FIX_TAG_MEMBER_CODE));
        assertTrue(uResponse.isSetField(FixConstants.FIX_TAG_SOURCE_TRANSACTION_NUMBER));
    }

    @Test
    void getResponseMessage_U28_Test() throws Exception {
        Message uResponse;
        //U28 RIGHTS_SUBSCRIPTION_REQUEST
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_RIGHTS_SUBSCRIPTION_REQUEST);
        message.setString(FixConstants.FIX_TAG_REFERENCE, IConstants.STRING_11);

        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);
        assertEquals(FixConstants.FIX_VALUE_35_RIGHTS_SUBSCRIPTION_RESPONSE,uResponse.getHeader().getString(MsgType.FIELD));
        assertEquals(IConstants.STRING_0, uResponse.getString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE));
        assertEquals(IConstants.STRING_11, uResponse.getString(FixConstants.FIX_TAG_REFERENCE));
        assertTrue(uResponse.isSetField(FixConstants.FIX_TAG_SOURCE_TRANSACTION_NUMBER));
    }

    @Test
    void getResponseMessage_U70_Test() throws Exception {
        Message uResponse;
        //U70 SUBSCRIPTION_COMMON_REQUEST
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_SUBSCRIPTION_COMMON_REQUEST);
        message.getHeader().setInt(MsgSeqNum.FIELD, 123);
        message.setString(FixConstants.FIX_TAG_CLIENT_REQUEST_ID, IConstants.STRING_11);
        message.setString(FixConstants.FIX_TAG_REFERENCE, IConstants.STRING_11);
        message.setString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE, IConstants.STRING_0);
        message.setString(FixConstants.FIX_TAG_SYSTEM_ID, IConstants.STRING_11);
        message.setInt(OrderQty.FIELD, IConstants.CONSTANT_ONE_1);
        message.setInt(FixConstants.FIX_TAG_CLIENT_REQUEST_ID, IConstants.CONSTANT_TEN_10);

        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);
        assertEquals(FixConstants.FIX_VALUE_35_SUBSCRIPTION_RESPONSE,uResponse.getHeader().getString(MsgType.FIELD));
        assertEquals(IConstants.STRING_10, uResponse.getString(FixConstants.FIX_TAG_LM_REFERENCE_NB));
        assertEquals(IConstants.STRING_5, uResponse.getString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE));
        assertEquals(FixConstants.FIX_VALUE_35_SUBSCRIPTION_COMMON_REQUEST, uResponse.getString(RefMsgType.FIELD));
        assertEquals( IConstants.STRING_11, uResponse.getString(FixConstants.FIX_TAG_SYSTEM_ID));
    }

    @Test
    void getResponseMessage_U77_Test() throws Exception {
        Message uResponse;
        //U77 SUBSCRIPTION_CANCEL_REQUEST
        message.getHeader().setInt(MsgSeqNum.FIELD, 124);
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_SUBSCRIPTION_CANCEL_REQUEST);
        message.setInt(FixConstants.FIX_TAG_CLIENT_REQUEST_ID, IConstants.CONSTANT_TEN_10);
        message.setString(FixConstants.FIX_TAG_REFERENCE, IConstants.STRING_11);
        message.setString(FixConstants.FIX_TAG_SYSTEM_ID, IConstants.STRING_10);
        message.setString(FixConstants.FIX_TAG_NIN, "NIN");
        message.setString(Symbol.FIELD, "1010");
        message.setString(FixConstants.FIX_SUBMISSION_DATE, "19970221-11:10:59");
        message.setString(FixConstants.FIX_TAG_MEMBER_CODE, "001");
        message.setString(FixConstants.FIX_TAG_ACCOUNT_NUMBER, "001");

        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);
        assertEquals(FixConstants.FIX_VALUE_35_SUBSCRIPTION_CANCEL_RESPONSE,uResponse.getHeader().getString(MsgType.FIELD));
        assertEquals(IConstants.STRING_10, uResponse.getString(FixConstants.FIX_TAG_LM_REFERENCE_NB));
        assertEquals( IConstants.STRING_11, uResponse.getString(FixConstants.FIX_TAG_REFERENCE));
        assertEquals( IConstants.STRING_10, uResponse.getString(FixConstants.FIX_TAG_SYSTEM_ID));
        assertEquals( "1010", uResponse.getString(Symbol.FIELD));
        assertEquals(IConstants.STRING_6, uResponse.getString(FixConstants.FIX_TAG_TRANSACTION_RETURN_CODE));
        assertEquals(IConstants.STRING_1, uResponse.getString(FixConstants.FIX_SUBSCRIPTION_STATUS));
        assertEquals("NIN", uResponse.getString(FixConstants.FIX_TAG_NIN));
        assertEquals("19970221-11:10:59", uResponse.getString(FixConstants.FIX_SUBMISSION_DATE));

    }

    @Test
    void getResponseMessage_U79_Test() throws Exception {
        Message uResponse;
        //U79 RECONCILIATION_SUMMARY_REQUEST
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_RECONCILIATION_SUMMARY_REQUEST);
        message.setString(FixConstants.FIX_TAG_SYSTEM_ID, IConstants.STRING_10);
        message.setString(FixConstants.FIX_TAG_MEMBER_CODE, "001");
        message.setString(Symbol.FIELD, "1010");
        message.setString(FixConstants.FIX_SUBMISSION_DATE, "19970221-11:10:59");

        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);
        assertEquals(FixConstants.FIX_VALUE_35_RECONCILIATION_SUMMARY_RESPONSE,uResponse.getHeader().getString(MsgType.FIELD));
        assertEquals( IConstants.STRING_10, uResponse.getString(FixConstants.FIX_TAG_SYSTEM_ID));
        assertEquals( "001", uResponse.getString(FixConstants.FIX_TAG_MEMBER_CODE));
        assertEquals( "1010", uResponse.getString(Symbol.FIELD));
        assertEquals("19970221", uResponse.getString(FixConstants.FIX_TAG_START_DATE));
        assertEquals("19970221", uResponse.getString(FixConstants.FIX_TAG_END_DATE));
    }

    @Test
    void getResponseMessage_U81_Test() throws Exception {
        Message uResponse;
        //U81 OFFER_COMPANY_LIST_REQUEST
        message.getHeader().setString(MsgType.FIELD, FixConstants.FIX_VALUE_35_OFFER_COMPANY_LIST_REQUEST);

        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);
        assertEquals(FixConstants.FIX_VALUE_35_OFFER_COMPANY_LIST_RESPONSE,uResponse.getHeader().getString(MsgType.FIELD));
        assertEquals( "DFIXSimulator : SYMBOL", uResponse.getString(Symbol.FIELD));
        assertEquals( 100.00, uResponse.getDouble(FixConstants.FIX_TAG_ISSUE_PRICE));
        assertEquals( 100, uResponse.getInt(FixConstants.FIX_TAG_MIN_SUBSCRIPTION));
        assertEquals( 150, uResponse.getInt(FixConstants.FIX_TAG_MAX_SUBSCRIPTION));

        final List<Group> group9980 = uResponse.getGroups(FixConstants.FIX_TAG_BID_CNT);
        assertEquals(IConstants.CONSTANT_FIVE_5, group9980.size());

        //Undefined
        message.getHeader().setString(MsgType.FIELD, "U100");
        uResponse = (Message) TestUtils.invokePrivateMethod(umMessageHandlerSpy, "getResponseMessage", new Class[]{Message.class}, message);
        assertNull(uResponse);
    }

}
