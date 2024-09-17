package com.mubasher.oms.dfixrouter.constants;

public class SettingsConstants extends IConstants {

    public static final String ADMIN_PORT = "ADMIN_PORT";
    public static final String ADMIN_BIND_IP = "ADMIN_BIND_IP";
    public static final String ADMIN_ALLOWED_IPS = "ADMIN_ALLOWED_IPS";
    public static final String CONSOLE_ADMIN_PORT = "CONSOLE_ADMIN_PORT";
    public static final String CONSOLE_ADMIN_BIND_IP = "CONSOLE_ADMIN_BIND_IP";
    public static final String CONSOLE_ADMIN_ALLOWED_IPS = "CONSOLE_ADMIN_ALLOWED_IPS";
    public static final String HOSTS_COUNT = "HOSTS_COUNT";
    public static final String TRADING_SESSIONS_AUTO_CONNECT = "TRADING_SESSIONS_AUTO_CONNECT";
    public static final String ENABLE_WATCHDOG = "ENABLE_WATCHDOG";
    public static final String FALCON_IP = "FALCON_IP";
    public static final String FALCON_PORT = "FALCON_PORT";
    public static final String FALCON_IP_SECONDARY = "FALCON_IP_SECONDARY";
    public static final String FALCON_PORT_SECONDARY = "FALCON_PORT_SECONDARY";
    public static final String ENABLE_FALCON_REGISTRATION = "ENABLE_FALCON_REGISTRATION";
    public static final String CLUSTER_MEMBER_PREFIX = "DFIX";
    public static final String CLUSTER_NAME_DFIX = "DFIX_CLUSTER_NAME";
    public static final String CLUSTER_HEART_BEAT = "CLUSTER_HEART_BEAT";
    public static final String AUTO_FAILOVER_ENABLED = "AUTO_FAILOVER_ENABLED";
    public static final String FAILOVER_ATTEMPTS = "FAILOVER_ATTEMPTS";
    public static final String FAILOVER_INTERVAL = "FAILOVER_INTERVAL";
    public static final String IS_PROCESS_EXCHANGE_MSG = "IS_PROCESS_EXCHANGE_MSG";
    public static final String CLUBBING_TIME_OUT = "CLUBBING_TIME_OUT";
    public static final String CLUBBED_MSG_DELAY = "CLUBBED_MSG_DELAY";
    public static final String IS_SIMULATOR_ON = "IS_SIMULATOR_ON";
    public static final String CLUBBING_RATIO = "CLUBBING_RATIO";
    public static final String FORWARD_QUEUE_SLEEP = "FORWARD_QUEUE_SLEEP";
    public static final String IS_CLUBBING_ENABLED = "IS_CLUBBING_ENABLED";
    public static final String EXCHANGE_LEVEL_GROUPING = "EXCHANGE_LEVEL_GROUPING";
    public static final String JMS_SESSION_COUNT = "JMS_SESSION_COUNT";
    public static final String JMS_GRP_ID_PROPERTY = "JMSXGroupID";
    public static final String FIX_LOG_TYPE = "FIX_LOG_TYPE";
    public static final String SIM_ORDER_CREATE_QUEUED = "SIM_ORDER_CREATE_QUEUED";
    public static final String IS_DAILY_MANUAL_RESTART = "IS_DAILY_MANUAL_RESTART";

    public static final String SSL_VERSION = "SSL_VERSION";
    public static final String DAILY_CACHE_CLEAR_TIME = "DAILY_CACHE_CLEAR_TIME";
    public static final String PASSWORD = "PASSWORD";
    public static final String FALCON_AUTHENTICATION_NODE_CATEGORY = "FALCON_AUTHENTICATION_NODE_CATEGORY";
    public static final String FALCON_AUTHENTICATION_NODE_PASSWORD = "FALCON_AUTHENTICATION_NODE_PASSWORD";

    public static final String FALCON_SSL_AGENT_ENABLED = "FALCON_SSL_AGENT_ENABLED";
    public static final String FALCON_SSL_CLIENT_KEYSTORE = "FALCON_SSL_CLIENT_KEYSTORE";
    public static final String FALCON_SSL_CLIENT_ENC_KEYSTORE_PASSWORD = "FALCON_SSL_CLIENT_ENC_KEYSTORE_PASSWORD";
    public static final String FALCON_SSL_CLIENT_TRUSTSTORE = "FALCON_SSL_CLIENT_TRUSTSTORE";
    public static final String FALCON_SSL_CLIENT_ENC_TRUSTSTORE_PASSWORD = "FALCON_SSL_CLIENT_ENC_TRUSTSTORE_PASSWORD";

    public static final String SSL_ENABLED = "SSL_ENABLED";
    public static final String ADMIN_SSL_ENABLED = "ADMIN_SSL_ENABLED";
    public static final String KEYSTORE_PATH = "KEYSTORE_PATH";
    public static final String ENC_KEYSTORE_PASSWORD = "ENC_KEYSTORE_PASSWORD";
    public static final String TRUSTSTORE_PATH = "TRUSTSTORE_PATH";
    public static final String ENC_TRUSTSTORE_PASSWORD = "ENC_TRUSTSTORE_PASSWORD";
    public static final String SIM_PROCESS_ICM_ORDERS = "SIM_PROCESS_ICM_ORDERS";
    public static final String BLOCK_DUPLICATE_BY_CLIORDID = "BLOCK_DUPLICATE_BY_CLIORDID";
    public static final String IS_MQ_CLUSTER = "IS_MQ_CLUSTER";

    public static final String UNPLACED_PROCESS_START_DELAY = "UNPLACED_PROCESS_START_DELAY";
    public static final String UNPLACED_PROCESSING_INTERVAL = "UNPLACED_PROCESSING_INTERVAL";
    public static final String UNPLACED_PROCESS_START_WAITING_INTERVAL = "UNPLACED_PROCESS_START_WAITING_INTERVAL";
    public static final String UNPLACED_PROCESSING_SPLIT_SIZE = "UNPLACED_PROCESSING_SPLIT_SIZE";


    private SettingsConstants() {
        super();
    }
}
