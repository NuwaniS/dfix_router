package com.mubasher.oms.dfixrouter.constants;

/**
 * Created by janakar on 10/7/2016.
 */
public class IConstants {

    public static final int APPLICATION_MESSAGE_RECEIVED = 23041;
    public static final int SESSION_CONNECTED = 23210;
    public static final int SESSION_DISCONNECTED = 23211;
    public static final int SESSION_STATUS = 23212;
    public static final int DFIX_INFORMATION = 22420;
    public static final String SESSION_SECTION_NAME = "session";
    public static final String SESSION_IDENTIFIER = "SessionIdentifier";
    public static final String SESSION_WEEK_DAYS = "Weekdays";
    public static final String SETTING_CONNECTION_TYPE = "ConnectionType";
    public static final String INITIATOR_CONNECTION_TYPE = "initiator";
    public static final String ACCEPTOR_CONNECTION_TYPE = "acceptor";
    public static final String INITIATOR_ADDRESS = "SocketConnectHost";
    public static final String INITIATOR_PORT = "SocketConnectPort";
    public static final String ACCEPTOR_ADDRESS = "AllowedRemoteAddresses";
    public static final String SETTING_DATA_DICTIONARY = "DataDictionary";
    public static final String SETTING_APP_DATA_DICTIONARY = "AppDataDictionary";
    public static final String SETTING_TRANSPORT_DATA_DICTIONARY = "TransportDataDictionary";
    public static final String ACCEPTOR_VIEW_PORT = "0";
    public static final String SETTING_LOGON_USER_DEFINED_TAGS = "LogonUserDefined";
    public static final String SETTING_USER_DEFINED_TAGS = "UserDefined";
    public static final String SETTING_USER_DEFINED_TO_OMS_TAGS = "UserDefinedToOMS";
    public static final String SETTING_USER_DEFINED_HEADER_TO_OMS_TAGS = "UserDefinedHeaderToOMS";
    public static final String SETTING_APPMSG_TARGETCOMPID = "AppMsgTargetCompID";
    public static final String SETTING_REPEATING_GRP_TAGS = "RepeatingGrpTags";
    public static final String SETTING_TAG_MODIFICATION = "TagModification";
    public static final String SETTING_TAG_MODIFICATION_TAG = "Tag";
    public static final String SETTING_TAG_SEND_TO_OMS = "SendToOMS";
    public static final String SETTING_YES = "Y";
    public static final String SETTING_NO = "N";
    public static final String SETTING_DFIX_ID = "DFIX_ID";
    public static final String MANUAL_FILE = "./system/DFIXRouter.txt";
    public static final String ALL_SESSIONS = "ALL";
    public static final String STRING_SESSION_DOWN = "DOWN";
    public static final String STRING_SESSION_CONNECTED = "CONNECTED";
    public static final String STRING_SESSION_DISCONNECTED = "DISCONNECTED";
    public static final String SESSION_SENT_LOGON = "SENT LOGON";
    public static final String HOST_MEMBER_PREFIX = "OMS";
    public static final String FD = "\u0001";
    public static final String FS = "\u001C";
    public static final String DS = "\u0002";
    public static final String SS = "\u0003";
    public static final String MAP_EXCHANGE = "SessionID";
    public static final String MAP_MESSAGE = "MessageData";
    public static final String MAP_EVENT_DATA = "EventData";
    public static final String MAP_TYPE = "MessageType";
    public static final String MAP_EVENT_TYPE = "EventType";
    public static final String MAP_SEQUENCE = "ClientMsgID";
    public static final String MIDDLEWARE_JMS = "JMS";
    public static final String MIDDLEWARE_MQ = "MQ";
    public static final String STRING_SEQUENCE = "SEQUENCE";
    public static final String LICENSE_FOREVER = "*";
    public static final int DEFAULT_FILL_QUANTITY = 10;
    public static final int DEFAULT_ORDER_HANDLER_COUNT = 10;
    public static final String DEFAULT_IP = "127.0.0.1";
    public static final String LOCALHOST = "localhost";
    public static final String IPV6_LOOPBACK = "0:0:0:0:0:0:0:1";
    public static final String SETTING_TAG_VALIDATE_MESSAGE_FLOW = "ValidateMessageFlow";
    public static final String SETTING_TAG_NEW_MESSAGE_RATE = "NewMessageRate";
    public static final String SETTING_TAG_AMEND_MESSAGE_RATE = "AmendMessageRate";
    public static final String SETTING_TAG_MESSAGE_FLOW_WINDOW_SIZE = "WindowSize";
    public static final int DEFAULT_FLOW_WINDOW_SIZE = 60;
    public static final String SETTING_TAG_NEW_WINDOW_LIMIT = "NewMessageWindowLimit";
    public static final String SETTING_TAG_AMEND_WINDOW_LIMIT = "AmendMessageWindowLimit";
    public static final String SETTING_TAG_DUPLICATE_WINDOW_LIMIT = "DuplicateMessageWindowLimit";
    public static final int SETTING_FIX_LOG_TYPE_SEPERATE_FILE = 1;
    public static final int STRING_FORMAT_PAD = 5;
    public static final String SETTING_RECONNECT_INTERVAL = "ReconnectInterval";
    public static final String SETTING_TAG_IS_REJECT_MESSAGE = "IsRejectMessage";
    public static final String SETTING_TAG_IS_SCH_PASS_RESET = "ScheduledPasswordReset";
    public static final String SETTING_TAG_USER_NAME = "UserName";
    public static final String SETTING_TAG_INSTANT_AUTO_SYNC_SEQ_NO = "InstantAutoSyncSeqNo";
    public static final String SSL_KEY_STORE_ARG = "javax.net.ssl.keyStore";
    public static final String SSL_KEY_STORE_PASS_ARG = "javax.net.ssl.keyStorePassword";
    public static final String SSL_TRUST_STORE_ARG = "javax.net.ssl.trustStore";
    public static final String SSL_TRUST_STORE_PASS_ARG = "javax.net.ssl.trustStorePassword";
    public static final String ENCRYPTION_KEY = "@1B2c3D4e5F6g7H8";
    public static final String DEFAULT_KEYSTORE_PATH = "./system/server.keystore" ;
    public static final String GNU_DECRYPT_KEY = "@1B2c3D4e5F6g7H8";
    public static final String DEFAULT_TRUSTSTORE_PATH = "./system/server.truststore";
    public static final String DEFAULT_KEY_STORE_PASS = "123456";
    public static final String SETTING_IGNORE_DROP_COPY = "IgnoreDropCopy";
    public static final String SETTING_ICM_DROP_COPY_SESSION = "ICMDropCopySession";

    public static final boolean CONSTANT_FALSE = false;
    public static final boolean CONSTANT_TRUE = true;
    public static final String DEFAULT_TENANT_CODE = "DEFAULT_TENANT";

    //File and other resource paths
    public static final String LICENSE_PATH = "./system/LicenseDetails.license";
    public static final String PUBLIC_KEY_PATH = "./system/DFIXRTR_Public.der";
    public static final String SETTING_FILE_PATH = "./config/settings.ini";

    public static final int CONSTANT_MINUS_1 = -1 ;
    public static final int CONSTANT_ZERO_0 = 0 ;
    public static final int CONSTANT_ONE_1 = 1 ;
    public static final int CONSTANT_TWO_2 = 2;
    public static final int CONSTANT_THREE_3 = 3;
    public static final int CONSTANT_FIVE_5 = 5 ;
    public static final int CONSTANT_TEN_10 = 10;
    public static final int CONSTANT_ONE_FIFTY = 150 ;
    public static final int CONSTANT_TWO_THOUSAND_2000 = 2000;
    public static final int CONSTANT_FOUR_THOUSAND_4000 = 4000;
    public  static final String STRING_MINUS_1 = "-1";
    public  static final String STRING_0 = "0";
    public  static final String STRING_1 = "1";
    public  static final String STRING_2 = "2";
    public  static final String STRING_5 = "5";
    public  static final String STRING_6 = "6";
    public  static final String STRING_10 = "10";
    public  static final String STRING_11 = "11";
    public  static final String STRING_1000 = "1000";
    public static final String COMMA_PATTERN = ",";

    protected IConstants() {
        super();
    }

    public static String getStringFromat(int stringSize) {
        return "%-${StringSize}s".replace("${StringSize}", Integer.toString(stringSize));
    }
}
