package com.mubasher.oms.dfixrouter.system;


import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;

import java.io.*;
import java.util.*;


public class Settings {

    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.system.Settings");
    private static final String CFG_FILE_NAME = "./config/fix.cfg";
    private static final String SETTING_FILE_NAME = "./config/settings.ini";
    private static final String ENC_SECRET_FILE_NAME = "./config/mgmt-users.properties";
    private static final String SIM_CFG_FILE_NAME = "./config/sim.cfg";
    private static final String SIM_SETTINGS_FILE_NAME = "./config/simulator.ini";
    private static Properties properties;
    private static List<Host> hostList = null;
    private static Map<Integer, Integer> hostIdMap = null;  //host.getOmsId(), host.getId()
    private static boolean simulatorOn = false;
    private static double clubbingRatio = 0;
    private static long clubbingTimeOut = 0L;
    private static boolean isSimultaneousOrdReqOrdQueProcessing = true;
    private static long forwardQueueSleep = 5;
    private static String sslVersion = "TLS";

    private static Properties passwordProperties;

    private Settings() {
        super();
    }

    public static String getPassword() {
        return passwordProperties.getProperty(SettingsConstants.PASSWORD);
    }

    // refresh the password from the file and return it
    public static String refreshAndGetPassword() {
        loadPassword();
        return getPassword();
    }


    public static void setPassword(String encryptedPassword) throws IOException {
        passwordProperties.setProperty(SettingsConstants.PASSWORD, encryptedPassword);
        try (OutputStream outputStream = new FileOutputStream(ENC_SECRET_FILE_NAME)) {
            passwordProperties.store(outputStream, null);
        }
    }

    public static void load() {
        load(SETTING_FILE_NAME);
    }

    public static void load(String file) {
        loadSettings(file);
        loadPassword();
    }

    private static void loadSettings(String file) {
        InputStream oIn;
        try {
            properties = new Properties();
            oIn = new FileInputStream(file);
            properties.load(oIn);
            oIn.close();
            loadHosts();
            if (IConstants.SETTING_YES.equalsIgnoreCase(getProperty(SettingsConstants.IS_SIMULATOR_ON))) {
                File simulatorFile = new File(SIM_SETTINGS_FILE_NAME);
                simulatorOn = simulatorFile.exists();
                if (simulatorOn) {
                    oIn = new FileInputStream(simulatorFile);
                    properties.load(oIn);
                    oIn.close();
                }
            }
            if (IConstants.SETTING_YES.equalsIgnoreCase(getProperty(SettingsConstants.IS_CLUBBING_ENABLED))) {
                if (getString(SettingsConstants.CLUBBING_RATIO) != null) {
                    clubbingRatio = Double.parseDouble(getString(SettingsConstants.CLUBBING_RATIO));
                }
                if (getString(SettingsConstants.CLUBBING_TIME_OUT) != null) {
                    clubbingTimeOut = Long.parseLong(getString(SettingsConstants.CLUBBING_TIME_OUT));
                }
            }
            if (IConstants.SETTING_NO.equalsIgnoreCase(getProperty(SettingsConstants.SIM_ORDER_CREATE_QUEUED))) {
                isSimultaneousOrdReqOrdQueProcessing = false;
            }

            if (getInt(SettingsConstants.FORWARD_QUEUE_SLEEP) > 0) {
                forwardQueueSleep = Settings.getInt(SettingsConstants.FORWARD_QUEUE_SLEEP);
            }

            final String sslVersionProp = getProperty(SettingsConstants.SSL_VERSION, true);
            if (sslVersionProp != null
                    && sslVersionProp.trim().length() > 0) {
                sslVersion = sslVersionProp.trim();
            }
            Log4j2Handler.setCompID(getProperty(IConstants.SETTING_DFIX_ID));
        } catch (Exception e) {
            logger.error("Error loading Settings File:" + e.getMessage(), e);
            DFIXRouterManager.exitApplication(0);
        }
    }

    //reads encrypted password from encrypted_password.dat file for telnet session
    private static void loadPassword() {
        try (InputStream oInPw = new FileInputStream(ENC_SECRET_FILE_NAME)) {
                passwordProperties = new Properties();
                passwordProperties.load(oInPw);
        } catch (Exception e) {
            logger.error("Error loading Password File:" + e.getMessage(), e);
            DFIXRouterManager.exitApplication(0);
        }
    }

    public static String getString(String s) {
        try {
            return properties.getProperty(s);
        } catch (Exception e) {
            logErrorProperty(s, e);
            DFIXRouterManager.exitApplication(0);
            return null;
        }
    }

    public static String getProperty(String s, boolean isLogError) {
        String sData = null;
        try {
            if (properties.containsKey(s)) {
                sData = properties.getProperty(s);
            }
        } catch (Exception e) {
            if (isLogError) {
                logErrorProperty(s, e);
            }
            sData = "";
        }
        return sData;
    }

    public static String getProperty(String key, String defaultVal) {
        String data = getProperty(key, true);
        if (data == null) {
            return defaultVal;
        }
        return data;
    }

    public static String getProperty(String s) {
        return getProperty(s, true);
    }

    public static int getInt(String s) {
        try {
            String sData = properties.getProperty(s);
            return Integer.parseInt(sData);
        } catch (Exception e) {
            logErrorProperty(s, e);
            return 0;
        }
    }

    public static void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    private static void loadHosts() {
        hostList = new ArrayList<>(getInt(SettingsConstants.HOSTS_COUNT));
        hostIdMap = new HashMap<>();

        if (IConstants.SETTING_YES.equalsIgnoreCase(getProperty(SettingsConstants.ENABLE_FALCON_REGISTRATION))) {
            logger.info("Hosts Loading Skipped. Hosts will be set at OMS Registration");
            return;
        }

        for (int a = 1; a <= getInt(SettingsConstants.HOSTS_COUNT); a++) {
            Host host = new Host();
            host.setId(a);
            host.setOmsId(Settings.getProperty("HOST" + a + "_OMS_ID") == null ? a : Settings.getInt("HOST" + a + "_OMS_ID"));
            host.setIp(Settings.getProperty("HOST" + a + "_IP"));
            host.setPort(Settings.getInt("HOST" + a + "_PORT"));
            host.setReqQName(Settings.getProperty("HOST" + a + "_FROM_QUEUE"));
            host.setResQName(Settings.getProperty("HOST" + a + "_TO_QUEUE"));
            host.setClubQName(Settings.getProperty("HOST" + a + "_CLUBBED_QUEUE"));
            host.setMiddleware(Settings.getProperty("HOST" + a + "_MIDDLEWARE"));
            host.setContextFactory(Settings.getProperty("HOST" + a + "_INITIAL_CONTEXT_FACTORY"));
            host.setProviderURL(Settings.getProperty("HOST" + a + "_EJB_CLIENT_PROVIDER_URL"));
            host.setConnectionFactory(Settings.getProperty("HOST" + a + "_CONNECTION_FACTORY"));
            host.setUserName(Settings.getProperty("HOST" + a + "_USERNAME"));
            host.setPassword(Settings.getProperty("HOST" + a + "_PASSWORD"));
            host.setIntermediateQueue(Settings.getProperty("HOST" + a + "_INTERMEDIATE_QUEUE"));
            if (host.getIntermediateQueue() != null) {
                host.setIntermediateQueueCount(Settings.getInt("HOST" + a + "_INTERMEDIATE_QUEUE_COUNT"));
                host.setIcmDropCopyQueueCount(Settings.getProperty("HOST" + a + "_ICM_DROP_COPY_QUEUE_COUNT") == null ?
                        IConstants.CONSTANT_ZERO_0 : Settings.getInt("HOST" + a + "_ICM_DROP_COPY_QUEUE_COUNT"));
            }
            host.setuMessageQueue(Settings.getProperty("HOST" + a + "_U_MESSAGE_QUEUE"));
            host.setChannel(Settings.getProperty("HOST" + a + "_CHANNEL_NAME"));
            host.setUrlPkgPrefixes(Settings.getProperty("HOST" + a + "_URL_PKG_PREFIXES"));
            host.setUrlPkgPrefixesValue(Settings.getProperty("HOST" + a + "_URL_PKG_PREFIXES_VALUE"));
            host.setReqQCount(Settings.getInt("HOST" + a + "_FROM_QUEUE_COUNT"));
            host.setSSLCipherSuite(Settings.getProperty("HOST" + a + "_SSL_CIPHER_SUITE"));
            host.setMqQueueManager(Settings.getProperty("HOST" + a + "_MQ_QUEUE_MANAGER"));
            host.setFipsEnabled(IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty("HOST" + a + "_FIPS_ENABLED")));

            final String supportedMsgType = Settings.getString("HOST" + a + "_SUPPORTED_MESSAGE_TYPE");
            if(supportedMsgType !=null &&
                    !supportedMsgType.isEmpty()){
                StringTokenizer supportedMessageType = new StringTokenizer(supportedMsgType,",");
                while (supportedMessageType.hasMoreTokens()){
                    host.getSupportedMessageTypes().add(supportedMessageType.nextToken());
                }

            }
            hostList.add(host);
            hostIdMap.put(host.getOmsId(), host.getId());
        }
    }

    public static List<Host> getHostList() {
        return hostList;
    }

    public static Map<Integer, Integer> getHostIdMap() {
        return hostIdMap;
    }

    public static int getHostIdForOmsId(int omsId) {
        return hostIdMap.get(omsId) == null ? -1 : hostIdMap.get(omsId);
    }

    private static void logErrorProperty(String propName, Throwable e) {
        logger.error("Error while loading Settings Property : " + propName + " " + e.getMessage(), e);
    }

    public static String getCfgFileName() {
        return CFG_FILE_NAME;
    }

    public static String getSimCfgFileName() {
        return SIM_CFG_FILE_NAME;
    }

    public static boolean isSimulatorOn() {
        return simulatorOn;
    }

    public static double getClubbingRatio() {
        return clubbingRatio;
    }

    public static void setClubbingRatio(double clubbingRatio) {
        Settings.clubbingRatio = clubbingRatio;
    }

    public static long getClubbingTimeOut() {
        return clubbingTimeOut;
    }

    public static void setClubbingTimeOut(long clubbingTimeOut) {
        Settings.clubbingTimeOut = clubbingTimeOut;
    }

    public static boolean isSimultaneousOrdReqOrdQueProcessing() {
        return isSimultaneousOrdReqOrdQueProcessing;
    }

    public static long getForwardQueueSleep() {
        return forwardQueueSleep;
    }

    public static String getSslVersion() {
        return sslVersion;
    }
}
