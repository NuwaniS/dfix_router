package com.mubasher.oms.dfixrouter.server.admin;

import com.mubasher.oms.dfixrouter.constants.AdminCommands;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.server.fix.flowcontrol.FlowController;
import com.mubasher.oms.dfixrouter.util.stores.TradingMarketStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import quickfix.ConfigError;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.SessionID;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Created by Nilaan L on 7/23/2024.
 */
class ConsoleAdminClientProxyTest {

    @Mock
    ServerSocket serverSocketMock;
    @Mock
    Socket socketMock;
    @Mock
    AdminManager adminManager;
    ConsoleAdminClientProxy consoleAdminClientProxy;
    @Mock
    FIXClient fixClientMock;

    MockedStatic<FIXClient> fixClientMockedStatic;
    MockedStatic<AdminManager> adminManagerMockedStatic;
    MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic;

    @BeforeEach
    void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        adminManagerMockedStatic = Mockito.mockStatic(AdminManager.class);
        dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);
        fixClientMockedStatic = Mockito.mockStatic(FIXClient.class);

        fixClientMockedStatic.when(FIXClient::getFIXClient).thenReturn(fixClientMock);
        adminManagerMockedStatic.when(AdminManager::getInstance).thenReturn(adminManager);


        InputStream inputStream = new ByteArrayInputStream("Hello Server".getBytes());
        OutputStream outputStream = new ByteArrayOutputStream();

        when(serverSocketMock.accept()).thenReturn(socketMock);
        when(socketMock.getInputStream()).thenReturn(inputStream);
        when(socketMock.getOutputStream()).thenReturn(outputStream);

        InetAddress address = InetAddress.getLocalHost();
        when(socketMock.getInetAddress()).thenReturn(address);
        consoleAdminClientProxy = new ConsoleAdminClientProxy(socketMock);
    }

    @AfterEach
    void tearDown() {
        fixClientMockedStatic.close();
        adminManagerMockedStatic.close();
        dfixRouterManagerMockedStatic.close();
    }

    @ParameterizedTest
    @EnumSource(AdminCommands.class)
//    @ValueSource(strings = {"RELOAD", "SHUTDOWN", "RESET_OUT_SEQ", "RESET_IN_SEQ", "CONNECT", "DISCONNECT", "ACTIVATE", "PASSIVATE", "EOD", "SHOW_STATUS", "EXIT","QUIT", "SET_MARKET_STATUS" })
    void processAdminCmds(AdminCommands cmdType) throws ConfigError, IOException, InvalidMessage {
        String cmd;
        String output;

        switch (cmdType) {
            case RELOAD:
                //reload TDWL-CL session
                cmd = "reload,TDWL-CL";
                when(fixClientMock.reload(any(String.class))).thenReturn(IConstants.CONSTANT_TRUE);
                output = consoleAdminClientProxy.processAdminCmds(cmd);
                Assertions.assertEquals("TDWL-CL reloaded.", output);

                //reload all session
                cmd = "reload";
                when(fixClientMock.reload()).thenReturn(new ArrayList<>());
                output = consoleAdminClientProxy.processAdminCmds(cmd);
                Assertions.assertEquals("No sessions reloaded.", output);
                break;

            case SHUTDOWN:
                // shutdown
                cmd = "shutdown";
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(adminManager, Mockito.times(IConstants.CONSTANT_ONE_1)).stopDFIXRouter();
                dfixRouterManagerMockedStatic.verify(() -> DFIXRouterManager.exitApplication(IConstants.CONSTANT_ZERO_0), Mockito.times(IConstants.CONSTANT_ONE_1));
                break;

            case RESET_OUT_SEQ:
                //RESET_OUT_SEQ
                cmd = "reset_out_seq,TDWL-CL,10";
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(adminManager, Mockito.times(IConstants.CONSTANT_ONE_1)).resetOutSequence("TDWL-CL", IConstants.CONSTANT_TEN_10);
                break;

            case RESET_IN_SEQ:
                //RESET_IN_SEQ
                cmd = "reset_in_seq,TDWL-CL,10";
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(adminManager, Mockito.times(IConstants.CONSTANT_ONE_1)).resetInSequence("TDWL-CL", IConstants.CONSTANT_TEN_10);
                break;
            case CONNECT:
                //CONNECT all sessions
                cmd = "connect,all";
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(adminManager, Mockito.times(IConstants.CONSTANT_ONE_1)).connectSession("all");
                break;
            case DISCONNECT:
                //DISCONNECT all sessions
                cmd = "disconnect,all";
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(adminManager, Mockito.times(IConstants.CONSTANT_ONE_1)).disconnectSession("all");
                break;

            case ACTIVATE:
                //ACTIVATE,ALL
                cmd = "activate";
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(adminManager, Mockito.times(IConstants.CONSTANT_ONE_1)).startDFIXRouter();
                break;
            case PASSIVATE:

                //PASSIVATE,ALL
                cmd = "passivate";
                Mockito.reset(adminManager);
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(adminManager, Mockito.times(IConstants.CONSTANT_ONE_1)).stopDFIXRouter();
                break;
            case EOD:
                //EOD
                cmd = "eod,all";
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(adminManager, Mockito.times(IConstants.CONSTANT_ONE_1)).runEod("all");
                break;
            case SHOW_STATUS:
                //SHOW_STATUS
                cmd = "show_status";
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(adminManager, Mockito.times(IConstants.CONSTANT_ONE_1)).showStatus();
                break;
            case EXIT:
            case QUIT:
                //EXIT , QUIT
                cmd = "quit";
                consoleAdminClientProxy.processAdminCmds(cmd);
                Mockito.verify(socketMock, Mockito.times(1)).close();
                break;
            case SET_MARKET_STATUS:
                try (MockedStatic<TradingMarketStore> tradingMarketStoreMockedStatic = Mockito.mockStatic(TradingMarketStore.class)) {

                    tradingMarketStoreMockedStatic.when(() -> TradingMarketStore.getTradingMarket("KSE", "REG")).thenReturn(null);
                    cmd = "set_market_status,KSE,REG,ACCEPTANCE";
                    output = consoleAdminClientProxy.processAdminCmds(cmd);

                    Assertions.assertEquals("Trading Market with, Exchange: KSE MarketCode: REG is not available", output);
                }
                break;
            case RELEASE:
                cmd = "release,TDWL";
                SessionID sessionId = new SessionID("FIX.4.2", "SLGM0", "TDWL");
                Mockito.when(fixClientMock.getSessionID("TDWL")).thenReturn(sessionId);
                FlowController flowControllerMock = Mockito.mock(FlowController.class);
                Map<SessionID, FlowController> flowControllerMap = new HashMap<>();
                flowControllerMap.put(sessionId, flowControllerMock);
                Mockito.when(fixClientMock.getFlowControllers()).thenReturn(flowControllerMap);

                output = consoleAdminClientProxy.processAdminCmds(cmd);
                Assertions.assertEquals("Flow Control re-initiated to the session: TDWL", output);
                break;
            case STORE_MESSAGE:
                cmd = "store_message,TDWL-CL,10,8=FIXT.1.1\u00019=000354\u000135=8\u000149=SLGM0\u000156=TDWL\u000134=8970\u000157=I0050001\u000152=20220803-06:30:00.535\u000137=202208031199\u000111=220802001057471\u0001";
                SessionID sessionId1 = new SessionID("FIX.4.2", "SLGM0", "TDWL");
                Mockito.when(fixClientMock.getSessionID("TDWL-CL")).thenReturn(sessionId1);
                Mockito.when(fixClientMock.storeMessage(sessionId1, IConstants.CONSTANT_TEN_10, cmd.split(",")[3].trim())).thenReturn(IConstants.CONSTANT_TRUE);
                output = consoleAdminClientProxy.processAdminCmds(cmd);

                Assertions.assertEquals("Message : " + cmd.split(",")[3].trim() + " is stored in " + cmd.split(",")[1].trim(), output);
                break;
            case SEND_MESSAGE:
                cmd = "send_message,TDWL_CL,8=FIXT.1.1\u00019=000354\u000135=8\u000149=SLGM0\u000156=TDWL\u000134=8970\u000157=I0050001\u000152=20220803-06:30:00.535\u000137=202208031199\u000111=220802001057471\u0001";
                output = consoleAdminClientProxy.processAdminCmds(cmd);
                Assertions.assertEquals("Message : " + cmd.split(",")[2].trim() + " is sent to " + cmd.split(",")[1].trim(), output);
                break;

            case GET_MESSAGES:
                cmd = "get_messages,TDWL-CL,10,150";
                SessionID sessionId2 = new SessionID("FIX.4.2", "SLGM0", "TDWL");
                Mockito.when(fixClientMock.getSessionID("TDWL-CL")).thenReturn(sessionId2);
                ArrayList<Message> messages = new ArrayList<>();
                messages.add(new Message());
                messages.add(new Message());
                Mockito.when(fixClientMock.getMessages(sessionId2, IConstants.CONSTANT_TEN_10, IConstants.CONSTANT_ONE_FIFTY)).thenReturn(messages);
                output = consoleAdminClientProxy.processAdminCmds(cmd);
                Assertions.assertEquals("9=010=167\n9=010=167\n", output);
                break;
            case SEND_SESSION_LIST:
            case SEND_SESSIONS_SEQUENCE:
            case EXIT_MONITOR:
            case CLOSE:
                output = consoleAdminClientProxy.processAdminCmds(cmdType.toString());
                Assertions.assertEquals("Invalid InputUsage: type ? for help", output);
                break;
            case CHANGE_PASSWORD:
                output = consoleAdminClientProxy.processAdminCmds(cmdType.toString());
                Assertions.assertEquals("Password change is only available from Host VM.", output);
                break;
            default:
                Assertions.fail("Undefined input" + cmdType);

        }

    }
}
