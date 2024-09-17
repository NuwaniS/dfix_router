package com.mubasher.oms.dfixrouter.logs;

/**
 * Created by isurut on 7/3/2017.
 */
public interface DFIXLogHandlerI {
    //=========================================================
    //---------------------------INFO--------------------------

    void info(LogEventsEnum logEvent, String message);

    void info(String lineNum, String txnID, LogEventsEnum logEvent, String message);

    void info(String txnID, LogEventsEnum logEvent, String message);

    void info(String message);

    //---------------------------INFO--------------------------
    //=========================================================
    //---------------------------DEBUG-------------------------

    void debug(String lineNum, String txnID, LogEventsEnum logEvent, String message);

    void debug(String lineNum, String txnID, LogEventsEnum logEvent, String message, Throwable e);

    void debug(String txnID, LogEventsEnum logEvent, String message);

    void debug(LogEventsEnum logEvent, String message);

    void debug(String message);

    void debug(String message, Throwable e);

    void debug(String lineNum, String message, Throwable e);

    //---------------------------DEBUG-------------------------
    //=========================================================
    //---------------------------TRACE-------------------------

    void trace(String lineNum, String txnID, LogEventsEnum logEvent, String message, Throwable e);

    void trace(String txnID, LogEventsEnum logEvent, String message);

    void trace(String lineNum, String txnID, LogEventsEnum logEvent, String message);

    void trace(LogEventsEnum logEvent, String message);

    void trace(String message);

    void trace(String message, Throwable e);

    void trace(String lineNum, String message, Throwable e);

    //---------------------------TRACE-------------------------
    //=========================================================
    //---------------------------ERROR-------------------------

    void error(String lineNum, String txnID, LogEventsEnum logEvent, String message);

    void error(String lineNum, String txnID, LogEventsEnum logEvent, String message, Throwable e);

    void error(String txnID, LogEventsEnum logEvent, String message, Throwable e);

    void error(String txnID, LogEventsEnum logEvent, String message);

    void error(LogEventsEnum logEvent, String message);

    void error(LogEventsEnum logEvent, String message, Throwable e);

    void error(String message, Throwable e);

    void error(String message);

    void error(String lineNum, String message, Throwable e);

    //---------------------------ERROR------------------------
    //========================================================
    //---------------------------WARN-------------------------

    void warn(String lineNum, String txnID, LogEventsEnum logEvent, String message);

    void warn(String lineNum, String txnID, LogEventsEnum logEvent, String message, Throwable e);

    void warn(String txnID, LogEventsEnum logEvent, String message);

    void warn(String txnID, LogEventsEnum logEvent, String message, Throwable e);

    void warn(LogEventsEnum logEvent, String message);

    void warn(String message);

    void warn(String message, Throwable e);

    void warn(LogEventsEnum logEvent, String message, Throwable e);

    //---------------------------WARN-------------------------
    //========================================================
    //---------------------------ELK-------------------------

    void elklog(String txnID, LogEventsEnum logEvent, String message);

    void elklog(String lineNum, String txnID, LogEventsEnum logEvent, String message);

    void elklog(LogEventsEnum logEvent, String message);

    final class Constants {
        public static final String TXN_ID_DEFAULT = "TXN-DFLT";
        public static final String LINE_NO_DEFAULT = "-1";

        private Constants() {
        }
    }

    //---------------------------ELK-------------------------
    //========================================================
}
