package com.mubasher.oms.dfixrouter.util;

/**
 * Created by IntelliJ IDEA.
 * User: prabodha
 * Date: Sep 30, 2009
 * Time: 4:38:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class FilterLog {

    public FilterLog() {
        super();
    }

    //filer mina logs in quickfix
    public void filterMinaLogs() {
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        rootLogger.setLevel(java.util.logging.Level.OFF);
    }
}
