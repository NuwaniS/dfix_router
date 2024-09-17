package com.mubasher.oms.dfixrouter.server.admin.license;

import com.mubasher.oms.dfixrouter.beans.IpDuration;
import com.mubasher.oms.dfixrouter.beans.License;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.exception.LicenseException;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;
import com.mubasher.oms.dfixrouter.system.Settings;
import com.mubasher.oms.dfixrouter.util.ValidateSessions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;
import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

class LicenseValidatorTest {

    public static final String RUN_TIME_LOCATION = System.getProperty("base.dir") + "/src/main/external-resources";
    public static final String PUBLIC_KEY_PATH = RUN_TIME_LOCATION + IConstants.PUBLIC_KEY_PATH.substring(1);
    public static final String LICENSE_PATH = RUN_TIME_LOCATION + IConstants.LICENSE_PATH.substring(1);
    public static final String CFG_PATH = RUN_TIME_LOCATION + Settings.getCfgFileName().substring(1);;
    private static final String CMD_ARG_ALLOWED_IP = "allowed_ip";
    private static final String CMD_ARG_ALLOWED_SESSIONS = "allowed_sessions";
    private static final String PRIVATE_KEY_FILE = "DFIXRTR_Private.der";
    private static final String INPUT_SEPERATOR = ",";
    private static final String CMD_ARG_DURATIONS = "durations";
    private static final String CMD_ARG_LICENSE_START_DATE = "license_start_date";
    private static final String CMD_ARG_LICENSE_PARALLEL_DFIX_COUNT = "parallal_dfix_count";
    private static PrivateKey privateKey;
    private static PublicKey publicKey;
    private static final String SAMPLE_BUILD_CODE = "Optional Input parameters are mentioned within [], Sample build Code:  mvn clean install"
            + " -D" + CMD_ARG_ALLOWED_IP + "=127.0.0.1" + INPUT_SEPERATOR +"127.0.0.1"
            + " -D" + CMD_ARG_ALLOWED_SESSIONS + "=5"
            + " [-D" + CMD_ARG_DURATIONS + "=" + IConstants.LICENSE_FOREVER + INPUT_SEPERATOR + "5]"
            + " [-D" + CMD_ARG_LICENSE_START_DATE + "=2017-10-25]"
            + " [-D" + CMD_ARG_LICENSE_PARALLEL_DFIX_COUNT + "=1]";
    public static final int DEFAULT_LICENSE_PERIOD_IN_MONTHS = 12;
    public static final byte DEFAULT_LICENSE_PARALLEL_DFIX_COUNT = 1;
    public static final String DATE_FORMAT = "yyyy-MM-dd";

    @Spy
    private static LicenseValidator licenseValidator = new LicenseValidator();
    @Spy
    ValidateSessions validateSessionsTest;

    @Spy
    static DFIXRouterManager dfixRouterManagerTest = DFIXRouterManager.getInstance();

    @BeforeAll
    public static void loadKeys() throws Exception {
        final ClassLoader classLoader = LicenseValidatorTest.class.getClassLoader();
        String path = classLoader.getResource(PRIVATE_KEY_FILE).getPath();
        URI uri = new URI(path.trim().replaceAll("\\u0020", "%20"));
        ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(uri.getPath()));
        privateKey = (PrivateKey) inputStream.readObject();
        Assertions.assertNotNull(privateKey, "Private key is not available.");

        try {
            licenseValidator.validatePublicKey(PUBLIC_KEY_PATH);
        } catch (LicenseException e) {
            Assertions.fail(e.getMessage());
        }
        inputStream = new ObjectInputStream(new FileInputStream(PUBLIC_KEY_PATH));
        publicKey = (PublicKey) inputStream.readObject();

        Assertions.assertNotNull(publicKey, "Public key is not available.");

        Assertions.assertEquals(privateKey.getAlgorithm(), publicKey.getAlgorithm(), "Private and Public Keys are not in Same Algorithm");

        File file = new File(LICENSE_PATH);
        if (file.exists()){
            Assertions.assertTrue(file.delete(), "Current License file cannot be deleted.");
        }
    }

    @AfterAll
    public static void createLicenseFile() throws Exception {
        try (MockedStatic<Settings> settingsMockedStatic = Mockito.mockStatic(Settings.class)) {
            settingsMockedStatic.when(Settings::getCfgFileName).thenReturn(CFG_PATH);


            String inputIp = System.getProperty(CMD_ARG_ALLOWED_IP);
            if (inputIp == null || inputIp.isEmpty()) {
                inputIp = InetAddress.getLocalHost().getHostName();
            }
            License license;
            final File file = new File(LICENSE_PATH);
            if (file.exists()) {
                Assertions.assertTrue(file.delete(), "Current License file cannot be deleted.");
            }
            String validatedIPs = validateInputIp(inputIp);
            byte validatedSessionCount = Byte.MAX_VALUE;
            if (System.getProperty(CMD_ARG_ALLOWED_SESSIONS) != null) {
                validatedSessionCount = validateInputBytes(System.getProperty(CMD_ARG_ALLOWED_SESSIONS));
            }
            String durations = "";
            Date licenseStartDate = validateLicenseStartDate(System.getProperty(CMD_ARG_LICENSE_START_DATE));
            if (System.getProperty(CMD_ARG_DURATIONS) != null) {
                durations = validateInputDurations(System.getProperty(CMD_ARG_DURATIONS));
            }
            byte allowedMaxParallelDfix = DEFAULT_LICENSE_PARALLEL_DFIX_COUNT;
            if (System.getProperty(CMD_ARG_LICENSE_PARALLEL_DFIX_COUNT) != null) {
                allowedMaxParallelDfix = validateInputBytes(System.getProperty(CMD_ARG_LICENSE_PARALLEL_DFIX_COUNT));
            }
            writeLicenseFile(validatedIPs, validatedSessionCount, LICENSE_PATH, durations, licenseStartDate, allowedMaxParallelDfix);
            Assertions.assertTrue(file.exists(), "License file Creation failed");

            final String currentIp = InetAddress.getLocalHost().getHostAddress();
            final String currentHostName = InetAddress.getLocalHost().getHostName();
            if (validatedIPs.contains(currentIp) || validatedIPs.contains(currentHostName)) {
                license = dfixRouterManagerTest.validateLicense(PUBLIC_KEY_PATH, LICENSE_PATH).getLicense();
            } else {
                license = licenseValidator.loadLicenseDetails(PUBLIC_KEY_PATH, LICENSE_PATH);
            }
            Assertions.assertEquals(Arrays.stream(validatedIPs.split(INPUT_SEPERATOR)).collect(Collectors.toSet()), license.getAllowedIPs(), "License File is not created with inserted IPs");
            Assertions.assertEquals(validatedSessionCount, license.getAllowedSessions(), "License File is not created with inserted Sessions Count");
        }
    }

    private static Date validateLicenseStartDate(String property) {
        Date licensStartDate = null;
        if (property == null || property.trim().length() == 0){
            licensStartDate = new Date(System.currentTimeMillis());
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            try {
                licensStartDate = sdf.parse(property);
            } catch (ParseException e) {
                Assertions.fail("Date Format should be " + DATE_FORMAT);
            }
        }
        Assertions.assertNotNull(licensStartDate, "License Start Date not valid.");
        return licensStartDate;
    }

    private static String validateInputDurations(String property) {
        String[] durations = property.split(INPUT_SEPERATOR);
        for (String duration :
                durations) {
            int months = -1;
            if (duration.trim().length() > 0 && !duration.trim().equals(IConstants.LICENSE_FOREVER)){
                try {
                    months = Integer.parseInt(duration);
                } catch (NumberFormatException e) {
                    Assertions.fail("Invalid duration entered. Duration: " + duration);
                }
                if (months < 0){
                    Assertions.fail("Duration cannot be Negative. Duration: " + duration);
                }
            }
        }
        return property;
    }

    private static String validateInputIp(String inputIPs){
        Assertions.assertNotNull(inputIPs, "Allowed Ips should be passed as '" + CMD_ARG_ALLOWED_IP + "', "
                + SAMPLE_BUILD_CODE);
        return inputIPs;
    }

    private static byte validateInputBytes(String inputSessCount){
        byte allowedSessions = (byte) 0;
        Assertions.assertNotNull(inputSessCount, "Allowed Number of Sessions should be passed as '" + CMD_ARG_ALLOWED_SESSIONS + "', "
                + SAMPLE_BUILD_CODE);
        try {
            allowedSessions = Byte.parseByte(inputSessCount);
        } catch (NumberFormatException e) {
            Assertions.fail("Passed Sessions should be a valid Number, " + SAMPLE_BUILD_CODE);
        }

        if (Integer.parseInt(inputSessCount) > Byte.MAX_VALUE){
            Assertions.fail("Maximum Allowed sessions " + Byte.MAX_VALUE);
        }

        if (allowedSessions <= 0) {
            Assertions.fail("Passed Sessions should be a greater than Zero");
        }
        return allowedSessions;
    }

    private static void writeLicenseFile(String allowedIPs, byte allowedSessions, String fileLocation, String durations, Date licenseStartDate, byte allowedMaxParallalDfix) throws Exception {
        Cipher cipher = Cipher.getInstance(privateKey.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        License license = new License(licenseStartDate.getTime());
        license.setIpDurations(getIpDurations(allowedIPs, durations, licenseStartDate));
        license.setAllowedSessions(allowedSessions);
        license.setAllowedParallelDfixes(allowedMaxParallalDfix);
        checkAgainstCipher(license, cipher);
        final SealedObject sealedObject = new SealedObject(license, cipher);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(fileLocation));
        objectOutputStream.writeObject(sealedObject);
        objectOutputStream.close();
    }

    private static void checkAgainstCipher(License license, Cipher cipher) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(license);
        out.flush();
        int objectsize = baos.toByteArray().length - 4;
        if (objectsize > cipher.getOutputSize(objectsize)){
            int keySize = ((RSAKey)privateKey).getModulus().bitLength();
            int padSize = 11;
            int maxSize = (int)(Math.floor(keySize / 8) - padSize);
            Assertions.fail("Key Size can only hold upto " +  maxSize + " But, Object Size is " + objectsize);
        }
    }

    private static LinkedHashMap<String, IpDuration> getIpDurations(String allowedIPs, String durations, Date bundleDate) throws Exception {
        LinkedHashMap<String, IpDuration> ipDurations = new LinkedHashMap<>();
        String[] sArrayIps = allowedIPs.split(INPUT_SEPERATOR);
        String[] sArrayDurations = durations.split(INPUT_SEPERATOR);
        for (int i = 0; i < sArrayIps.length; i++){
            String ip = sArrayIps[i];
            int months = DEFAULT_LICENSE_PERIOD_IN_MONTHS;
            IpDuration ipDuration = new IpDuration(ip);
            if (sArrayDurations.length > i && sArrayDurations[i].trim().length() > 0){
                if (IConstants.LICENSE_FOREVER.equals(sArrayDurations[i])){
                    ipDuration.setForever(true);
                } else {
                    months = Integer.parseInt(sArrayDurations[i].trim());
                }
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(bundleDate);
            if (!ipDuration.isForever()) {
                cal.add(Calendar.MONTH, months);
                ipDuration.setExpiryDate(cal.getTimeInMillis());
            }
            ipDurations.put(ip, ipDuration);
        }
        return ipDurations;
    }

    @Test
    void checkKeys() throws Exception {
        String plainText = "PlainText";
        Cipher cipher = Cipher.getInstance(privateKey.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        final byte[] cipherText = cipher.doFinal(plainText.getBytes());
        Assertions.assertNotNull(cipherText, "Cipher Text is not created.");

        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        final byte[] decryptedText = cipher.doFinal(cipherText);

        Assertions.assertEquals(plainText, new String(decryptedText));
    }

    @Test
    void checkIpPass() throws Exception {
        String allowedIp = InetAddress.getLocalHost().getHostAddress();
        License license = new License(System.currentTimeMillis());
        IpDuration ipDuration = new IpDuration(allowedIp);
        license.addIpDuration(ipDuration);
        try {
            licenseValidator.validateIp(license);
        } catch (LicenseException e) {
            Assertions.assertFalse(license.getAllowedIPs().contains(allowedIp), "License Validator has issues.");
        }
    }

    @Test
    void checkIpFail() throws Exception {
        License license = new License(System.currentTimeMillis());

        try {
            licenseValidator.validateIp(license);
        } catch (LicenseException e) {
            Assertions.assertEquals(LicenseException.IP_VALIDATION_FAIL, e.getErrorCode(), "licenseValidator.validateIp() has issues.");
        }
        File file = new File(LICENSE_PATH);
        if (file.exists()){
            Assertions.assertTrue(file.delete(), "Test License file cannot be deleted.");
        }
    }

    @Test
    void checkSessionsPass() throws Exception {
        byte allowedSessions = 10;
        License license = new License(System.currentTimeMillis());
        license.setAllowedSessions(allowedSessions);
        final ClassLoader classLoader = getClass().getClassLoader();
        final String cfgFileName = "correctSessions.cfg";
        final String path = classLoader.getResource(cfgFileName).getPath();
        final URI uri = new URI(path.trim().replaceAll("\\u0020", "%20"));
        final File file = new File(uri.getPath());
        final FileInputStream in = new FileInputStream(file);
        final int testFileSessions = validateSessionsTest.getInstance().load(in);

        if (testFileSessions > allowedSessions){
            Assertions.fail("Test configuration file: " + cfgFileName + " cannot have more than " + allowedSessions + " sessions.");
        }
        licenseValidator.validateSessions(license, uri.getPath());
    }

    @Test
    void checkSessionsFail() throws Exception {
        byte allowedSessions = 1;
        License license = new License(System.currentTimeMillis());
        license.setAllowedSessions(allowedSessions);
        final ClassLoader classLoader = getClass().getClassLoader();
        final String cfgFileName = "correctSessions.cfg";
        final String path = classLoader.getResource(cfgFileName).getPath();
        final URI uri = new URI(path.trim().replaceAll("\\u0020", "%20"));
        final File file = new File(uri.getPath());
        final FileInputStream in = new FileInputStream(file);
        final int testFileSessions = validateSessionsTest.getInstance().load(in);

        if (testFileSessions < allowedSessions){
            Assertions.fail("Test configuration file: " + cfgFileName + " should have atleast " + allowedSessions + " sessions.");
        }
        try {
            licenseValidator.validateSessions(license, uri.getPath());
        } catch (LicenseException e) {
            Assertions.assertEquals(LicenseException.SESSION_VALIDATION_FAIL, e.getErrorCode(), "licenseValidator.validateSessions has issues.");
        }
    }

    @Test
    void publicKeyFileFailTest() {
        Assertions.assertThrows(LicenseException.class, () -> licenseValidator.loadLicenseDetails("", ""));
    }

    @Test
    void licenseFilePathFailTest() {
        Assertions.assertThrows(LicenseException.class, () -> licenseValidator.loadLicenseDetails(PUBLIC_KEY_PATH, ""));
    }

    @Test
    void validateSessionsFailTest() {
        Assertions.assertThrows(LicenseException.class, () -> licenseValidator.validateSessions(new License(System.currentTimeMillis()), ""));
    }

    @Test
    void validateExpiryPassTest() throws Exception {
        try {
            int duration0 = 1;
            String IP0 = InetAddress.getLocalHost().getHostAddress();
            String IP1 = "IP1";
            Date licensStartDate = new Date(System.currentTimeMillis());

            String IPs = IP0+ INPUT_SEPERATOR + IP1;

            String strDuration = duration0 + INPUT_SEPERATOR + IConstants.LICENSE_FOREVER;

            writeLicenseFile(IPs, (byte)1, LICENSE_PATH, strDuration, licensStartDate, DEFAULT_LICENSE_PARALLEL_DFIX_COUNT);
            License license = licenseValidator.loadLicenseDetails(PUBLIC_KEY_PATH, LICENSE_PATH);

            licenseValidator.validateExpiryPeriod(license);

            IpDuration ipDuration0 = license.getIpDuration(IP0);
            Assertions.assertFalse(ipDuration0.isForever(), "License File is not written Correctly: validateExpiryPassTest Fail - 0");

            IpDuration ipDuration1 = license.getIpDuration(IP1);
            Assertions.assertTrue(ipDuration1.isForever(), "License File is not written Correctly: validateExpiryPassTest Fail - 1");

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(licensStartDate);
            calendar.add(Calendar.MONTH, duration0);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            Assertions.assertEquals(ipDuration0.getExpiryDate(), calendar.getTime(), "License File is not written Correctly: validateExpiryPassTest Fail - 3");
        } finally {
            File file = new File(LICENSE_PATH);
            if (file.exists()){
                Assertions.assertTrue(file.delete(), "Test License file cannot be deleted.");
            }
        }
    }

    @Test
    void validateExpiryFailTest() throws Exception {
        try {
            String IP = InetAddress.getLocalHost().getHostAddress();
            int duration = -1;
            writeLicenseFile(IP, (byte)1, LICENSE_PATH, String.valueOf(duration), new Date(System.currentTimeMillis()), DEFAULT_LICENSE_PARALLEL_DFIX_COUNT);
            License license = licenseValidator.loadLicenseDetails(PUBLIC_KEY_PATH, LICENSE_PATH);

            try {
                licenseValidator.validateExpiryPeriod(license);
            } catch (LicenseException e) {
                Assertions.assertEquals(LicenseException.LICENSE_EXPIRED, e.getErrorCode(), "licenseValidator.validateExpiryPeriod() has issues.");
            }
        } finally {
            File file = new File(LICENSE_PATH);
            if (file.exists()){
                Assertions.assertTrue(file.delete(), "Test License file cannot be deleted.");
            }
        }
    }

    @Test
    void validateLicenseStartDateFailTest() throws Exception {
        try {
            int duration = 1;
            String IP = InetAddress.getLocalHost().getHostAddress();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date(System.currentTimeMillis()));
            calendar.add(Calendar.DATE, -1);
            writeLicenseFile(IP, (byte)1, LICENSE_PATH, String.valueOf(duration), calendar.getTime(), DEFAULT_LICENSE_PARALLEL_DFIX_COUNT);
            License license = licenseValidator.loadLicenseDetails(PUBLIC_KEY_PATH, LICENSE_PATH);

            try {
                licenseValidator.validateExpiryPeriod(license);
            } catch (LicenseException e) {
                Assertions.assertEquals(LicenseException.LICENSE_START_DATE_FAIL, e.getErrorCode(), "licenseValidator.validateExpiryPeriod() has issues.");
            }
        } finally {
            File file = new File(LICENSE_PATH);
            if (file.exists()){
                Assertions.assertTrue(file.delete(), "Test License file cannot be deleted.");
            }
        }
    }
}
