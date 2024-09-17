package com.mubasher.oms.dfixrouter.server.oms;

import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.objectspace.jgl.Queue;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.jms.JMSException;
import java.util.concurrent.TimeUnit;

class QueueExceptionListenerTest {

    @Mock
    MiddlewareHandler middlewareHandlerMock;
    Queue queueMock;
    QueueExceptionListener queueExceptionListenerSpy;

    private MockedStatic<DFIXRouterManager> dfixRouterManagerMockedStatic ;

    @BeforeEach
    public void setup(){
        dfixRouterManagerMockedStatic = Mockito.mockStatic(DFIXRouterManager.class);
    }
    @AfterEach
    public void tear() {
        dfixRouterManagerMockedStatic.close();
    }

    public void setExceptionListener(Class<? extends MiddlewareHandler> middlewareHandler) {
        middlewareHandlerMock = Mockito.mock(middlewareHandler);
        queueExceptionListenerSpy = Mockito.spy(new QueueExceptionListener(middlewareHandlerMock));
        queueMock = Mockito.spy(new com.objectspace.jgl.Queue());
        Mockito.doReturn("ToString mock").when(middlewareHandlerMock).toString();
        Mockito.doReturn(queueExceptionListenerSpy).when(middlewareHandlerMock).getQueueExceptionListener();
        Mockito.doReturn(queueMock).when(middlewareHandlerMock).getQueue();
    }

    @Test
    void startConnectionProcess_WAITING() {
        setExceptionListener(JMSListener.class);
        Mockito.doReturn(Thread.State.WAITING).when(queueExceptionListenerSpy).getState();
        queueExceptionListenerSpy.onException(new JMSException("Test Exception"));
    }

    @Test
    void startConnectionProcess_NEW() {
        setExceptionListener(JMSListener.class);
        Mockito.doReturn(Thread.State.NEW).when(queueExceptionListenerSpy).getState();
        queueExceptionListenerSpy.onException(new JMSException("Test Exception"));
        Mockito.verify(queueExceptionListenerSpy, Mockito.times(1)).start();
    }

    @Test
    void startConnectionProcess_flowTest() {
        setExceptionListener(JMSListener.class);
        Mockito.doReturn(Thread.State.TERMINATED).when(queueExceptionListenerSpy).getState();
        queueExceptionListenerSpy.onException(new JMSException("Test Exception"));
    }

    @Test
    void run_notActive() {
        setExceptionListener(JMSListener.class);
        queueExceptionListenerSpy.run();
    }

    private void run_activeFlowTest(boolean isConnected) throws Exception {
        Mockito.doReturn(true).when(middlewareHandlerMock).isActive();
        Mockito.doReturn(isConnected).when(middlewareHandlerMock).isConnected();
        dfixRouterManagerMockedStatic.when(() -> DFIXRouterManager.sleepThread(Mockito.anyLong())).then(
                invocation -> {
                    Mockito.doReturn(false).when(middlewareHandlerMock).isActive();
                    return null;
                });
        if (isConnected) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        Awaitility.await().atLeast(10, TimeUnit.MILLISECONDS).until(
                                ()-> {
                                    Mockito.doReturn(false).when(middlewareHandlerMock).isActive();
                                    synchronized (queueExceptionListenerSpy) {
                                        queueExceptionListenerSpy.notify();
                                    }
                                    return true;
                                });
                    } catch (Exception e) {
                    }
                }
            }.start();
        }
        queueExceptionListenerSpy.run();
        // run()  is directly called to execute the code under run method directly in the current thread (calling start() will fork new thread )
        if (!isConnected) {
            if (middlewareHandlerMock instanceof MiddlewareSender) {
                Mockito.verify((MiddlewareSender) middlewareHandlerMock, Mockito.times(1)).initializeConnection();
            } else if (middlewareHandlerMock instanceof MiddlewareListener) {
                Mockito.verify((MiddlewareListener) middlewareHandlerMock, Mockito.times(1)).initializeConnection();
            }
        }
    }

    @Test
    void run_notConnected() throws Exception {
        setExceptionListener(MiddlewareHandler.class);
        run_activeFlowTest(false);
    }

    @Test
    void run_notConnectedJmsListener() throws Exception {
        setExceptionListener(JMSListener.class);
        run_activeFlowTest(false);
    }

    @Test
    void run_notConnectedMQListener() throws Exception {
        setExceptionListener(MQListener.class);
        run_activeFlowTest(false);
    }
    @Test
    void run_notConnectedJmsSender() throws Exception {
        setExceptionListener(JMSSender.class);
        run_activeFlowTest(false);
    }

    @Test
    void run_notConnectedMQSender() throws Exception {
        setExceptionListener(MQSender.class);
        run_activeFlowTest(false);
    }

    @Test
    void run_connectedJmsListener() throws Exception{
        setExceptionListener(JMSListener.class);
        run_activeFlowTest(true);
    }

    @Test
    void run_connectedMQListener() throws Exception {
        setExceptionListener(MQListener.class);
        run_activeFlowTest(true);
    }
    @Test
    void run_connectedJmsSender() throws Exception{
        setExceptionListener(JMSSender.class);
        Mockito.doNothing().when((JMSSender)middlewareHandlerMock).run();
        run_activeFlowTest(true);
    }

    @Test
    void run_connectedMQSender() throws Exception {
        setExceptionListener(MQSender.class);
        Mockito.doNothing().when((MQSender)middlewareHandlerMock).run();
        run_activeFlowTest(true);
    }

}
