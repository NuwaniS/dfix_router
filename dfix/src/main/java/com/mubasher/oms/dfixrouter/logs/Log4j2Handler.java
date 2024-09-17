package com.mubasher.oms.dfixrouter.logs;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.system.Settings;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI.Constants.LINE_NO_DEFAULT;
import static com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI.Constants.TXN_ID_DEFAULT;

/**
 * Created by isurut on 7/3/2017.
 */
public class Log4j2Handler implements DFIXLogHandlerI {
    private static final Logger elkLogger = LogManager.getLogger("ELKLogger");
    private static final String LOG_MSG_FORMAT_PARAM_7 = "\u001F{}\u001F{}\u001F{}\u001F{}\u001F{}\u001F{}\u001F{}\u001F\n"; //lineNumber,txnID,eventType,ComponentName,ComponentID,ip,Message
    private static final String LOG_MSG_FORMAT_PARAM_8 = "\u001F{}\u001F{}\u001F{}\u001F{}\u001F{}\u001F{}\u001F{}\u001F{}\u001F\n"; //lineNumber,txnID,eventType,ComponentName,ComponentID,ip,Message,throwable
    private static final String LAYOUT_PATTERN = "%d{yyyy-MM-dd HH:mm:ss,SSS} %-15c{1} %msg%n";
    private static String compName = "DFIX-ROUTER";
    private static String compID;
    private static String ip;

    static {
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ip = IConstants.DEFAULT_IP;
        }
    }

    private final Logger logger;

    public Log4j2Handler(String clzName) {
        this.logger = LogManager.getLogger(clzName);
    }

    public static void setCompID(String compID) {
        Log4j2Handler.compID = compName + "-" + compID;
    }

    public static void addSessionLogger(String sessionId) {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        StringBuilder sb = new StringBuilder("logs/");
        sb.append(sessionId);
        if ((IConstants.SETTING_YES).equalsIgnoreCase(Settings.getProperty(SettingsConstants.IS_DAILY_MANUAL_RESTART))){
            sb.append("-").append(sdf.format(new Date()));
        }
        sb.append(".log");
        final String fileNameFormat = sb.toString();
        final String filePatter = "logs/" + sessionId + "-%d{yyyy-MM-dd}.log";
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        PatternLayout layout = PatternLayout.newBuilder().withConfiguration(config).withPattern(LAYOUT_PATTERN).build();
        TimeBasedTriggeringPolicy policy = TimeBasedTriggeringPolicy.newBuilder().withInterval(1).withModulate(true).build();
        DefaultRolloverStrategy strategy = DefaultRolloverStrategy.newBuilder().withFileIndex("nomax").withCompressionLevelStr("0").withConfig(config).build();
        RollingFileManager fileManager = RollingFileManager.getFileManager(fileNameFormat, filePatter
                , true, false, policy, strategy, null, layout, -1, false, true
                , null, null, null
                , config);
        policy.initialize(fileManager);
        RollingFileAppender appender = RollingFileAppender.newBuilder()
                .setName(sessionId)
                .withFileName(fileNameFormat)
                .withFilePattern(filePatter)
                .setLayout(layout)
                .withPolicy(policy)
                .withStrategy(strategy)
                .build();
        appender.start();
        config.addAppender(appender);

        AppenderRef ref = AppenderRef.createAppenderRef(sessionId, null, null);
        AppenderRef[] refs = new AppenderRef[]{ref};
        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.INFO, sessionId,
                "true", refs, null, config, null);
        loggerConfig.addAppender(appender, null, null);
        config.addLogger(sessionId, loggerConfig);
        ctx.updateLoggers();
    }

    //===============================================
    //--------------------INFO-----------------------

    @Override
    public void info(LogEventsEnum logEvent, String message) {
        info(LINE_NO_DEFAULT, TXN_ID_DEFAULT, logEvent, message);
    }

    @Override
    public void info(String lineNum, String txnID, LogEventsEnum logEvent, String message) {
        if (logger.isInfoEnabled())
            logger.info(LOG_MSG_FORMAT_PARAM_7, lineNum, txnID, logEvent.eventID, compName, compID, ip, message);
    }

    @Override
    public void info(String txnID, LogEventsEnum logEvent,  String message) {
        info(LINE_NO_DEFAULT, txnID, logEvent, message);
    }

    @Override
    public void info(String message) {
        info(LogEventsEnum.DEFAULT_EVENT, message);
    }

    //-------------------INFO-----------------------
    //==============================================
    //------------------DEBUG-----------------------

    @Override
    public void debug(String lineNum, String txnID, LogEventsEnum logEvent, String message) {
        if (logger.isDebugEnabled())
            logger.debug(LOG_MSG_FORMAT_PARAM_7, lineNum, txnID, logEvent.eventID, compName, compID, ip, message);
    }

    @Override
    public void debug(String lineNum, String txnID, LogEventsEnum logEvent, String message, Throwable e) {
        if (logger.isDebugEnabled())
            logger.debug(LOG_MSG_FORMAT_PARAM_8, lineNum, txnID, logEvent.eventID, compName, compID, ip, message, e);
    }

    @Override
    public void debug(String txnID, LogEventsEnum logEvent, String message) {
        debug(LINE_NO_DEFAULT, txnID, logEvent, message);
    }

    @Override
    public void debug(LogEventsEnum logEvent, String message) {
        debug(LINE_NO_DEFAULT, TXN_ID_DEFAULT, logEvent, message);
    }

    @Override
    public void debug(String message) {
        debug(LogEventsEnum.DEFAULT_EVENT, message);
    }

    @Override
    public void debug(String message, Throwable e) {
        debug(LINE_NO_DEFAULT, TXN_ID_DEFAULT, LogEventsEnum.DEFAULT_EVENT, message, e);
    }

    @Override
    public void debug(String lineNum, String message, Throwable e) {
        debug(lineNum, TXN_ID_DEFAULT, LogEventsEnum.DEFAULT_EVENT, message, e);
    }

    //------------------DEBUG-----------------------
    //==============================================
    //------------------TRACE-----------------------

    @Override
    public void trace(String lineNum, String txnID, LogEventsEnum logEvent, String message) {
        if (logger.isTraceEnabled())
            logger.trace(LOG_MSG_FORMAT_PARAM_7, lineNum, txnID, logEvent.eventID, compName, compID, ip, message);
    }

    @Override
    public void trace(String lineNum, String txnID, LogEventsEnum logEvent, String message, Throwable e) {
        if (logger.isTraceEnabled())
            logger.trace(LOG_MSG_FORMAT_PARAM_8, lineNum, txnID, logEvent.eventID, compName, compID, ip, message, e);
    }

    @Override
    public void trace(String txnID, LogEventsEnum logEvent, String message) {
        trace(LINE_NO_DEFAULT, txnID, logEvent, message);
    }

    @Override
    public void trace(LogEventsEnum logEvent, String message) {
        trace(LINE_NO_DEFAULT, TXN_ID_DEFAULT, logEvent, message);
    }

    @Override
    public void trace(String message) {
        trace(LogEventsEnum.DEFAULT_EVENT, message);
    }

    @Override
    public void trace(String message, Throwable e) {
        trace(LINE_NO_DEFAULT, TXN_ID_DEFAULT, LogEventsEnum.DEFAULT_EVENT, message, e);
    }

    @Override
    public void trace(String lineNum, String message, Throwable e) {
        trace(lineNum, TXN_ID_DEFAULT, LogEventsEnum.DEFAULT_EVENT, message, e);
    }

    //------------------TRACE-----------------------
    //==============================================
    //------------------ERROR-----------------------

    @Override
    public void error(String lineNum, String txnID, LogEventsEnum logEvent, String message) {
        if (logger.isErrorEnabled())
            logger.error(LOG_MSG_FORMAT_PARAM_7, lineNum, txnID, logEvent.eventID, compName, compID, ip, message);
    }

    @Override
    public void error(String lineNum, String txnID, LogEventsEnum logEvent, String message, Throwable e) {
        if (logger.isErrorEnabled())
            //If the last argument is a Throwable and print its StackTrace in log, it must NOT use up by a placeholder in the message pattern
            //Thus passing the pattern with param count except e -> LOG_MSG_FORMAT_PARAM_7
            logger.error(LOG_MSG_FORMAT_PARAM_7, lineNum, txnID, logEvent.eventID, compName, compID, ip, message, e);
            //lineNumber,txnID,eventType,eventType,ComponentID,IP,Message,isTagValueMsg/ErrorCode,StackTrace
    }

    @Override
    public void error(String txnID, LogEventsEnum logEvent, String message, Throwable e) {
        error(LINE_NO_DEFAULT, txnID, logEvent, message, e);
    }

    @Override
    public void error(String txnID, LogEventsEnum logEvent, String message) {
        error(LINE_NO_DEFAULT, txnID, logEvent, message);
    }

    @Override
    public void error(LogEventsEnum logEvent, String message) {
        error(LINE_NO_DEFAULT, TXN_ID_DEFAULT, logEvent, message);
    }

    @Override
    public void error(LogEventsEnum logEvent, String message, Throwable e) {
        error(LINE_NO_DEFAULT, TXN_ID_DEFAULT, logEvent, message, e);
    }

    @Override
    public void error(String message, Throwable e) {
        error(LINE_NO_DEFAULT, TXN_ID_DEFAULT, LogEventsEnum.DEFAULT_EVENT, message, e);
    }

    @Override
    public void error(String message) {
        error(LogEventsEnum.DEFAULT_EVENT, message);
    }

    @Override
    public void error(String lineNum, String message, Throwable e) {
        error(lineNum, TXN_ID_DEFAULT, LogEventsEnum.DEFAULT_EVENT, message, e);
    }

    //------------------ERROR-----------------------
    //==============================================
    //------------------WARN------------------------

    @Override
    public void warn(String lineNum, String txnID, LogEventsEnum logEvent, String message) {
        if (logger.isWarnEnabled())
            logger.warn(LOG_MSG_FORMAT_PARAM_7, lineNum, txnID, logEvent.eventID, compName, compID, ip, message);
    }

    @Override
    public void warn(String lineNum, String txnID, LogEventsEnum logEvent, String message, Throwable e) {
        if (logger.isWarnEnabled())
            logger.warn(LOG_MSG_FORMAT_PARAM_8, lineNum, txnID, logEvent.eventID, compName, compID, ip, message, e);
    }

    @Override
    public void warn(String txnID, LogEventsEnum logEvent, String message) {
        warn(LINE_NO_DEFAULT, txnID, logEvent, message);
    }

    @Override
    public void warn(String txnID, LogEventsEnum logEvent, String message, Throwable e) {
        warn(LINE_NO_DEFAULT, txnID, logEvent, message, e);
    }

    @Override
    public void warn(LogEventsEnum logEvent, String message) {
        warn(LINE_NO_DEFAULT, TXN_ID_DEFAULT, logEvent, message);
    }

    @Override
    public void warn(String message) {
        warn(LogEventsEnum.DEFAULT_EVENT, message);
    }

    @Override
    public void warn(String message, Throwable e) {
        warn(LINE_NO_DEFAULT, TXN_ID_DEFAULT, LogEventsEnum.DEFAULT_EVENT, message, e);
    }

    @Override
    public void warn(LogEventsEnum logEvent, String message, Throwable e) {
        warn(LINE_NO_DEFAULT, TXN_ID_DEFAULT, logEvent, message, e);
    }

    //------------WARN-------------------------
    //==============================================
    //------------------ELK-----------------------

    @Override
    public void elklog(String lineNum, String txnID, LogEventsEnum logEvent, String message) {
        elkLogger.info(LOG_MSG_FORMAT_PARAM_7, lineNum, txnID, logEvent.eventID, compName, compID, ip, message);
    }

    @Override
    public void elklog(String txnID, LogEventsEnum logEvent, String message) {
        elklog(LINE_NO_DEFAULT, txnID, logEvent, message);
    }

    @Override
    public void elklog(LogEventsEnum logEvent, String message) {
        elklog(LINE_NO_DEFAULT, TXN_ID_DEFAULT, logEvent, message);
    }

    //------------------ELK-----------------------
    //==============================================
}
