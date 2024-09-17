package com.mubasher.oms.dfixrouter.server;

import com.isi.security.GNUCrypt;
import com.mubasher.oms.dfixrouter.beans.Host;
import com.mubasher.oms.dfixrouter.beans.License;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.constants.SettingsConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.exception.LicenseException;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.admin.CacheManager;
import com.mubasher.oms.dfixrouter.server.admin.ConsoleAdminServer;
import com.mubasher.oms.dfixrouter.server.admin.UIAdminServer;
import com.mubasher.oms.dfixrouter.server.admin.license.ExpiryDateChecker;
import com.mubasher.oms.dfixrouter.server.admin.license.LicenseValidator;
import com.mubasher.oms.dfixrouter.server.cluster.ClusterThread;
import com.mubasher.oms.dfixrouter.server.cluster.DFIXCluster;
import com.mubasher.oms.dfixrouter.server.exchange.FromExchangeQueue;
import com.mubasher.oms.dfixrouter.server.exchange.ToExchangeQueue;
import com.mubasher.oms.dfixrouter.server.exchange.clubbing.ClubbedMessageTimer;
import com.mubasher.oms.dfixrouter.server.exchange.clubbing.ExchangeExecutionMerger;
import com.mubasher.oms.dfixrouter.server.fix.FIXClient;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.FilterLog;
import com.mubasher.oms.dfixrouter.util.WatchDogHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * This is the main class of the component and all the initializing happening here
 */
public class DFIXRouterManager {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.DFIXRouterManager");
    private static ToExchangeQueue toExchangeQueue = null;
    private static FromExchangeQueue fromExchangeQueue = null;
    private static DFIXCluster dfixCluster = null;
    private static boolean dfixRouterStarted = false;
    private static DFIXRouterManager dfixrouterManager = null;
    private static ClusterThread clusterThread = null;
    private static boolean isGraceFulClose = true;
    private static ScheduledExecutorService executor = null;
    private ExchangeExecutionMerger exchangeExecutionMerger = null;
    private ClubbedMessageTimer clubbedMessageTimer = null;
    private static boolean fixClientEnable = false;
    public static final String FROM_EXCHANGE_QUEUE_NAME = "FROM_EXCHANGE_QUEUE";

    public static void main(String[] args) {
        ExpiryDateChecker expiryDateChecker = null;
        System.setProperty("org.quickfixj.CharsetSupport.setCharSet", "Windows-1256");

        getInstance().enableClients();
        Settings.load(IConstants.SETTING_FILE_PATH);
        expiryDateChecker = initializeLicenceValidator();
        getInstance().initSSLCredentials(); //load keystore , truststore properties into System properties for SSL connection
        getInstance().runWatchDogAgent();
        FIXClient.getFIXClient().startFIXClient();
        FIXClient.getFIXClient().startSimulator();
        initDfixCluster(expiryDateChecker);
        initExecutors(expiryDateChecker);
        if (Settings.getProperty(SettingsConstants.IS_DAILY_MANUAL_RESTART, false) == null
            || (IConstants.SETTING_NO).equalsIgnoreCase(Settings.getProperty(SettingsConstants.IS_DAILY_MANUAL_RESTART, false))) {
            scheduleCacheClearManager();
        }

        try {
            sleepThread(2000);
            getInstance().startDFIXRouterManager();
        } catch (Exception e) {
            logger.error("Error at startDFIXRouterManager: " + e.getMessage(), e);
            exitApplication(0);
        }

        //Start Console Admin for Telnet clients.
        ConsoleAdminServer dfixAdmin = new ConsoleAdminServer();
        dfixAdmin.start();

        //Start Admin Server
        logger.debug("Starting UIAdminServer.....");
        UIAdminServer as = new UIAdminServer();
        as.start();

        addShutDownHook();

        if (!Settings.getHostList().isEmpty()
                && (IConstants.SETTING_NO).equalsIgnoreCase(Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG))){
            CacheManager.getInstance().sendCacheUpdateRequest();
        }
    }

    private static void addShutDownHook() {
        if (isGraceFulClose) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(200);
                        logger.info("Shutting down DFIXRTR ...");
                        DFIXRouterManager.getInstance().stopDFIXRouterManager();
                    } catch (InterruptedException e) {
                        logger.error("Error at shutting down DFIXRTR: " + e.getMessage(), e);
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }

    private static void initExecutors(ExpiryDateChecker expiryDateChecker) {
        if (expiryDateChecker != null) {
            executor = Executors.newScheduledThreadPool(5);
            executor.scheduleAtFixedRate(expiryDateChecker, ExpiryDateChecker.getCheckingIntervalInMin(), ExpiryDateChecker.getCheckingIntervalInMin(), TimeUnit.MINUTES);
        } else {
            executor = Executors.newScheduledThreadPool(4);
        }
    }

    private static void initDfixCluster(ExpiryDateChecker expiryDateChecker) {
        if (expiryDateChecker != null
                && expiryDateChecker.getLicense() != null
                && expiryDateChecker.getLicense().getAllowedParallelDfixes() > 1) {
            String clusterIPs = System.getProperty("jgroups.tcpping.initial_hosts");
            if (clusterIPs != null && clusterIPs.split(",").length > 1) {
                dfixCluster = DFIXCluster.getInstance(expiryDateChecker.getLicense().getAllowedParallelDfixes());
            }
        }
    }

    private static ExpiryDateChecker initializeLicenceValidator() {
        try {
            return getInstance().validateLicense(IConstants.PUBLIC_KEY_PATH, IConstants.LICENSE_PATH);
        } catch (LicenseException e) {
            logger.error("License Validation Failed: " + e.getMessage(), e);
            exitApplication(0);
        }
        return null;
    }

    /**
     * load keystore , truststore properties from settings.ini file into System properties
     * NOTE : JMS handler autoload ssl configuration from system JVM properties
     */
    private void initSSLCredentials() {
        if (IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.SSL_ENABLED))) {
            System.setProperty(IConstants.SSL_KEY_STORE_ARG,Settings.getProperty(SettingsConstants.KEYSTORE_PATH));
            System.setProperty(IConstants.SSL_TRUST_STORE_ARG,Settings.getProperty(SettingsConstants.TRUSTSTORE_PATH));
            try {
                if (System.getProperty(IConstants.SSL_KEY_STORE_ARG)!=null) {
                    System.setProperty(IConstants.SSL_KEY_STORE_PASS_ARG, GNUCrypt.decrypt(IConstants.GNU_DECRYPT_KEY, Settings.getProperty(SettingsConstants.ENC_KEYSTORE_PASSWORD)));
                    logger.info("SSL keystore credential loaded into System Parameter");
                }
                if (System.getProperty(IConstants.SSL_TRUST_STORE_ARG)!=null) {
                    System.setProperty(IConstants.SSL_TRUST_STORE_PASS_ARG, GNUCrypt.decrypt(IConstants.GNU_DECRYPT_KEY, Settings.getProperty(SettingsConstants.ENC_TRUSTSTORE_PASSWORD)));
                    logger.info("SSL truststore credential loaded into System Parameter");
                }
            } catch (InvalidKeyException e) {
                logger.error("Error loading SSL credentials " +e);
                DFIXRouterManager.exitApplication(0);
            }
        } else {
            logger.info("SSL Communication for components is not enabled");
        }
    }

    private static void scheduleCacheClearManager() {
        long initialDelay = 0l;
        String scheduleTime = Settings.getProperty(SettingsConstants.DAILY_CACHE_CLEAR_TIME, false);
        if (scheduleTime != null && !scheduleTime.isEmpty()) {
            String[] timeArray = scheduleTime.split(":");
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeArray[0]));
            calendar.set(Calendar.MINUTE, Integer.parseInt(timeArray[1]));
            calendar.set(Calendar.SECOND, Integer.parseInt(timeArray[2]));
            calendar.add(Calendar.DATE, 1);
            initialDelay = calendar.getTime().getTime() - System.currentTimeMillis();
        }
        executor.scheduleAtFixedRate(CacheManager.getInstance(), initialDelay, 86400000, TimeUnit.MILLISECONDS);
    }

    public ExpiryDateChecker validateLicense(String publicKeyPath, String licensePath) throws LicenseException {
        final LicenseValidator licenseValidator = new LicenseValidator();
        licenseValidator.validatePublicKey(publicKeyPath);
        final License license = licenseValidator.loadLicenseDetails(publicKeyPath, licensePath);
        licenseValidator.validateIp(license);
        licenseValidator.validateExpiryPeriod(license);
        if (isFixClientEnable()) {
            licenseValidator.validateSessions(license, Settings.getCfgFileName());
        }
        return ExpiryDateChecker.getInstance(licenseValidator, license);
    }

    public static DFIXRouterManager getInstance() {
        if (dfixrouterManager == null) {
            dfixrouterManager = new DFIXRouterManager();
        }
        return dfixrouterManager;
    }

    public static void exitApplication(int milliseconds) {
        System.exit(milliseconds);
    }

    public static void sleepThread(long iTime) {
        try {
            Thread.sleep(iTime);
        } catch (InterruptedException e) {
            logger.error("sleepThread Interrupted " + e.getMessage(), e);
        }
    }
    public void runWatchDogAgent() {
        try {
            if ((IConstants.SETTING_YES).equalsIgnoreCase(Settings.getProperty(SettingsConstants.ENABLE_WATCHDOG))) {
                WatchDogHandler.runWatchdogAgent();
                WatchDogHandler.waitForRegistration();
            }
        } catch (Exception e) {
            logger.error("Falcon agent starting failed: " + e.getMessage(), e);
            exitApplication(0);
        }
    }

    public void enableClients() {
        if (isFixCfgAvailable()) {
            setFixClientEnable(true);
        }
    }

    public boolean isFixCfgAvailable() {
        Path fixConfigPath = Paths.get(Settings.getCfgFileName());
        return Files.exists(fixConfigPath);
    }

    public String startDFIXRouterManager() throws DFIXConfigException {
        String output;
        if (isStarted()) {
            output = "DFIXRouter is already started.";
            logger.info(output);
        } else if (getDfixCluster() != null && !getDfixCluster().isAllowedToActivate()) {
            output = "Not allowed to activate: Primary DFIXRTR is already running :" + getDfixCluster().getPrimaryNode();
            logger.error(output);
        } else {
            if (dfixCluster != null) {
                dfixCluster.tellClusterMyStatus(true);
            }

            if (!Settings.getHostList().isEmpty()) {
                startToExchangeQueue();
                startFromExchangeQueue();
            }

            if (isFixClientEnable()) {
                logger.debug("Starting FixClient.....");
                FilterLog filerLog = new FilterLog();
                filerLog.filterMinaLogs();

                FIXClient.getFIXClient().loginToFIXGateway();
            }
            setDfixRouterStarted(true);

            if (dfixCluster != null) {
                startClusterThread();
            }
            if (IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.IS_CLUBBING_ENABLED))) {
                initExchangeExecutionClubbing();
            }
            output = "DFIXRouter Started";
        }
        return output;
    }

    public String stopDFIXRouterManager() {
        String output;
        if (isStarted()) {
            FIXClient.getFIXClient().stopFIXClient();
            stopFromExchangeQueue();
            stopToExchangeQueue();
            if (IConstants.SETTING_YES.equalsIgnoreCase(Settings.getProperty(SettingsConstants.IS_CLUBBING_ENABLED))) {
                stopExchangeExecutionClubbing();
            }
            setDfixRouterStarted(false);
            if (dfixCluster != null) {
                dfixCluster.tellClusterMyStatus(false);
            }
            if (Settings.isSimulatorOn()) {
                FIXClient.getFIXClient().stopFIXSimulator();
            }
            executor.shutdown();
            output = "DFIXRouter Stopped.";
        } else {
            output = "DFIXRouter is already stopped.";
            logger.info(output);
        }
        return output;
    }

    public boolean isStarted() {
        return dfixRouterStarted;
    }

    public static DFIXCluster getDfixCluster() {
        return dfixCluster;
    }

    public void startToExchangeQueue() throws DFIXConfigException {
        initializeToExchangeQueue();
        toExchangeQueue.startListenerQueueConnection();
    }

    public void startFromExchangeQueue() throws DFIXConfigException {
        initializeFromExchangeQueue();
        fromExchangeQueue.startSenderQueueConnection();
    }

    public void initializeFromExchangeQueue() {
        logger.info("Starting FromExchangeQueue.....");
        if (fromExchangeQueue == null) {
            setFromExchangeQueue(new FromExchangeQueue());
            executor.scheduleAtFixedRate(fromExchangeQueue, 0, 1, TimeUnit.SECONDS);
        }
    }

    public void initializeToExchangeQueue() {
        logger.info("Starting ToExchangeQueue.....");
        if (toExchangeQueue == null) {
            setToExchangeQueue(new ToExchangeQueue());
            executor.scheduleAtFixedRate(toExchangeQueue, (1000 - Calendar.getInstance().get(Calendar.MILLISECOND)), 1000, TimeUnit.MILLISECONDS);
        }
    }

    public void startToExchangeQueue(Host host) throws DFIXConfigException {
        initializeToExchangeQueue();
        toExchangeQueue.startListenerQueueConnection(host);
    }

    public void startFromExchangeQueue(Host host) throws DFIXConfigException {
        initializeFromExchangeQueue();
        fromExchangeQueue.startSenderQueueConnection(host);
    }

    public static void setDfixRouterStarted(boolean dfixRouterStarted) {
        DFIXRouterManager.dfixRouterStarted = dfixRouterStarted;
    }

    public void startClusterThread() {
        logger.info("Starting ClusterThread.....");
        if (clusterThread == null) {
            setClusterThread(new ClusterThread());
            executor.scheduleAtFixedRate(clusterThread, 0, 100, TimeUnit.SECONDS);
        }
        clusterThread.resetSessions();
    }

    public void initExchangeExecutionClubbing() {
        logger.info("Starting ExchangeExecutionClubbing.....");
        setExchangeExecutionMerger(new ExchangeExecutionMerger());
        getExchangeExecutionMerger().init();
        getExchangeExecutionMerger().start();
        setClubbedMessageTimer(new ClubbedMessageTimer());
        getClubbedMessageTimer().init();
        getClubbedMessageTimer().start();
    }

    public void stopFromExchangeQueue() {
        logger.info("Stopping FromExchangeQueue.....");
        if (fromExchangeQueue == null) {
            logger.info("FromExchangeQueue not started.");
        } else {
            fromExchangeQueue.stopSenderQueueConnection();
        }
    }

    public void stopToExchangeQueue() {
        logger.info("Stopping ToExchangeQueue.....");
        if (toExchangeQueue == null) {
            logger.info("ToExchangeQueue not started.");
        } else {
            toExchangeQueue.stopListenerQueueConnection();
        }
    }

    public void stopFromExchangeQueue(int omsId) {
        logger.info("Stopping FromExchangeQueue..... oms: " + omsId);
        if (fromExchangeQueue == null) {
            logger.info("FromExchangeQueue not started.");
        } else {
            fromExchangeQueue.stopSenderQueueConnection(omsId);
        }
    }

    public void stopToExchangeQueue(int hostId) {
        logger.info("Stopping ToExchangeQueue..... host Id: " + hostId);
        if (toExchangeQueue == null) {
            logger.info("ToExchangeQueue not started.");
        } else {
            toExchangeQueue.stopListenerQueueConnection(hostId);
        }
    }

    public void stopExchangeExecutionClubbing() {
        exchangeExecutionMerger.getExecutor().shutdown();
    }

    public static void setClusterThread(ClusterThread clusterThread) {
        DFIXRouterManager.clusterThread = clusterThread;
    }

    public static FromExchangeQueue getFromExchangeQueue() {
        return fromExchangeQueue;
    }

    public static void setFromExchangeQueue(FromExchangeQueue fromExchangeQueue) {
        DFIXRouterManager.fromExchangeQueue = fromExchangeQueue;
    }

    public static ToExchangeQueue getToExchangeQueue() {
        return toExchangeQueue;
    }

    public static void setToExchangeQueue(ToExchangeQueue toExchangeQueue) {
        DFIXRouterManager.toExchangeQueue = toExchangeQueue;
    }

    public static void setIsGraceFulClose(boolean isGraceFulClose) {
        DFIXRouterManager.isGraceFulClose = isGraceFulClose;
    }

    public ExchangeExecutionMerger getExchangeExecutionMerger() {
        return exchangeExecutionMerger;
    }

    public void setExchangeExecutionMerger(ExchangeExecutionMerger exchangeExecutionMerger) {
        this.exchangeExecutionMerger = exchangeExecutionMerger;
    }

    public ClubbedMessageTimer getClubbedMessageTimer() {
        return clubbedMessageTimer;
    }

    public void setClubbedMessageTimer(ClubbedMessageTimer clubbedMessageTimer) {
        this.clubbedMessageTimer = clubbedMessageTimer;
    }

    public static boolean isFixClientEnable() {
        return fixClientEnable;
    }

    public static void setFixClientEnable(boolean fixClientEnable) {
        DFIXRouterManager.fixClientEnable = fixClientEnable;
    }
}
