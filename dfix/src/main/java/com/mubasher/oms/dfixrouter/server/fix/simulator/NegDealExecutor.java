package com.mubasher.oms.dfixrouter.server.fix.simulator;

import com.mubasher.oms.dfixrouter.constants.SimulatorSettings;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.fix.FIXApplicationCommonLogic;
import com.mubasher.oms.dfixrouter.system.Settings;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.*;
import quickfix.fix44.TradeCaptureReportAck;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;

public class NegDealExecutor extends FIXApplicationCommonLogic implements Runnable {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.fix.simulator.NegDealExecutor");
    private Message message;
    private SessionID sessionID;

    public NegDealExecutor(Message message, SessionID sessionID) {
        this.message = message;
        this.sessionID = sessionID;
    }

    @Override
    public void run() {
        try {
            Message reportAck;
            if (!message.isSetField(TradeReportRefID.FIELD)) {
                reportAck = sendAcknowledgement(TradeReportType.SUBMIT);
                Thread.sleep(10000);
                if (message.getInt(LastShares.FIELD) != Settings.getInt(SimulatorSettings.REJECT_QTY)) {
                    sendTradeCaptureReport(reportAck, TradeReportType.ALLEGED);
                    Thread.sleep(10000);
                    sendTradeCaptureReport(reportAck, TradeReportType.SUBMIT);
                }
            }
            if (message.getInt(LastShares.FIELD) == Settings.getInt(SimulatorSettings.REJECT_QTY)){
                sendTradeCaptureReport(null, TradeReportType.ALLEGED);
            }
        } catch (Exception e) {
            logger.error("NegDealExecutor: " + e.getMessage(), e);
        }
    }

    private Message sendAcknowledgement(int tradeReportType) throws FieldNotFound {
        Message reportAck = new Message();
        reportAck.getHeader().setString(MsgType.FIELD, TradeCaptureReportAck.MSGTYPE);
        reportAck.setString(TradeReportID.FIELD, message.getString(TradeReportID.FIELD));
        reportAck.setInt(TradeReportTransType.FIELD, TradeReportTransType.NEW);
        reportAck.setInt(TradeReportType.FIELD, tradeReportType);
        reportAck.setString(TradeReportRefID.FIELD, FIXApplicationCommonLogic.getExecutionId());
        if (message.getInt(LastShares.FIELD) == Settings.getInt(SimulatorSettings.REJECT_QTY)) {
            reportAck.setInt(TrdRptStatus.FIELD, TrdRptStatus.REJECTED);
        } else {
            reportAck.setInt(TrdRptStatus.FIELD, TrdRptStatus.ACCEPTED);
        }
        reportAck.setString(ExecID.FIELD, FIXApplicationCommonLogic.getExecutionId());
        sendToTarget(reportAck, sessionID);
        return reportAck;
    }

    private void sendTradeCaptureReport(Message reportAck, int tradeReportType) throws FieldNotFound {
        Message tradeCaptureReport = (Message) message.clone();
        tradeCaptureReport.setUtcTimeStamp(TransactTime.FIELD, LocalDateTime.now());
        tradeCaptureReport.setString(TradeDate.FIELD, new SimpleDateFormat("yyyyMMdd").format(new Date()));
        tradeCaptureReport.setChar(ExecType.FIELD, ExecType.TRADE);
        if (reportAck != null) {
            tradeCaptureReport.setString(TradeReportID.FIELD, reportAck.getString(TradeReportRefID.FIELD));
        } else {
            tradeCaptureReport.setString(TradeReportID.FIELD, FIXApplicationCommonLogic.getExecutionId());
        }
        tradeCaptureReport.setString(TradeReportRefID.FIELD, message.getString(TradeReportID.FIELD));
        tradeCaptureReport.setChar(MatchStatus.FIELD, MatchStatus.COMPARED_MATCHED_OR_AFFIRMED);
        if (message.getInt(NoSides.FIELD) > 1 && reportAck != null) {
            tradeCaptureReport.setString(MatchType.FIELD, MatchType.ONE_PARTY_TRADE_REPORT);
        } else {
            tradeCaptureReport.setString(MatchType.FIELD, MatchType.TWO_PARTY_TRADE_REPORT);
        }
        tradeCaptureReport.setInt(TrdType.FIELD, TrdType.PRIVATELY_NEGOTIATED_TRADES);
        tradeCaptureReport.setString(TrdMatchID.FIELD, message.getString(TradeReportID.FIELD));
        tradeCaptureReport.setInt(TradeReportType.FIELD, tradeReportType);
        if (reportAck != null) {
            tradeCaptureReport.setString(ExecID.FIELD, reportAck.getString(ExecID.FIELD));
        }
        sendToTarget(tradeCaptureReport, sessionID);
    }
}
