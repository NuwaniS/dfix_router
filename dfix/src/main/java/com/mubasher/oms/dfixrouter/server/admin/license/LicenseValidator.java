package com.mubasher.oms.dfixrouter.server.admin.license;

import com.mubasher.oms.dfixrouter.beans.IpDuration;
import com.mubasher.oms.dfixrouter.beans.License;
import com.mubasher.oms.dfixrouter.exception.LicenseException;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.util.ValidateSessions;

import javax.crypto.SealedObject;
import javax.xml.bind.DatatypeConverter;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;

public class LicenseValidator {
    public static final String CHECKSUM_ALGORITHM = "SHA-1";
    public static final String PUBLIC_KEY_FILE_CHECKSUM = "37DA4409F14C1870AE3CE1CAC00716FAD48811AE";
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.admin.license.LicenseValidator");
    private static final String[] LOOP_BACK_CHECK_STRINGS = {"loopback"};
    private static boolean isFirst = true;

    public License loadLicenseDetails(String publicKeyPath, String licensePath) throws LicenseException {
        logger.debug("License Loading started from File: " + licensePath);
        final PublicKey publicKey = loadPublicKey(publicKeyPath);
        File licenseFile = new File(licensePath);
        License license;

        try (ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(licenseFile))) {
            SealedObject sealedObject = (SealedObject) objectInputStream.readObject();
            license = (License) sealedObject.getObject(publicKey);
            logger.info("License: " + license);
        } catch (Exception e) {
            throw new LicenseException(e);
        }
        return license;
    }

    private PublicKey loadPublicKey(String publicKeyPath) throws LicenseException {
        PublicKey publicKey;
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(publicKeyPath))) {
            publicKey = (PublicKey) inputStream.readObject();
        } catch (Exception e) {
            throw new LicenseException(e);
        }
        return publicKey;
    }

    public void validatePublicKey(String filePath) throws LicenseException {
        try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
            BufferedInputStream bis = new BufferedInputStream(fileInputStream);
            byte[] block = new byte[1024];
            int length;
            while ((length = bis.read(block)) > 0) {
                digest.update(block, 0, length);
            }
            String expectedCheckSum = DatatypeConverter.printHexBinary(digest.digest());
            if (!expectedCheckSum.equalsIgnoreCase(PUBLIC_KEY_FILE_CHECKSUM)) {
                throw new LicenseException(LicenseException.LICENSE_FILE_CHECKSUM_FAIL, PUBLIC_KEY_FILE_CHECKSUM, expectedCheckSum);
            }
        } catch (LicenseException e) {
            throw e;
        } catch (Exception e) {
            throw new LicenseException(e);
        }
    }

    public void validateIp(License license) throws LicenseException {
        String currentIp;
        String currentHostName;
        boolean isLoopBack;
        try {
            currentIp = InetAddress.getLocalHost().getHostAddress();
            currentHostName = InetAddress.getLocalHost().getHostName();
            isLoopBack = isLoopBackIp(currentIp);
        } catch (Exception e) {
            logger.error("Local Host Ip is not loaded.");
            throw new LicenseException(e);
        }

        if (!license.getAllowedIPs().contains(currentIp) || isLoopBack) {
            if (!license.getAllowedIPs().contains(currentHostName)) {
                throw new LicenseException(LicenseException.IP_VALIDATION_FAIL, license.getAllowedIPs().toString(), (new StringBuilder(currentIp).append(", ").append(currentHostName)).toString());
            } else {
                logger.info("Host Name Validated: " + currentHostName);
            }
        } else {
            logger.info("IP Validated: " + currentIp);
        }
    }

    private boolean isLoopBackIp(String ip) throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netInt : Collections.list(interfaces)) {
            if (isLoopBackInterface(netInt.getDisplayName()) && checkInetAddresses(netInt, ip)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLoopBackInterface(String intDispName) {
        for (String loopBackCheckString : LOOP_BACK_CHECK_STRINGS) {
            if (intDispName.toLowerCase().contains(loopBackCheckString)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkInetAddresses(NetworkInterface netInt, String ip) {
        Enumeration<InetAddress> inetAddresses = netInt.getInetAddresses();
        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            if (inetAddress.getHostAddress().equals(ip)) {
                return true;
            }
        }
        return false;
    }

    public void validateSessions(License license, String cfgFile) throws LicenseException {
        int allowedSessions = license.getAllowedSessions();
        int configuration;
        try (FileInputStream fileInputStream = new FileInputStream(cfgFile)) {
            configuration = ValidateSessions.getInstance().load(fileInputStream);
        } catch (Exception e) {
            throw new LicenseException(e);
        }

        if (configuration > allowedSessions) {
            throw new LicenseException(LicenseException.SESSION_VALIDATION_FAIL, String.valueOf(allowedSessions), String.valueOf(configuration));
        } else {
            logger.info("Sessions Count Validated. Allowed Sessions: " + allowedSessions + ", Available Sessions: " + configuration);
        }
    }

    public void validateExpiryPeriod(License license) throws LicenseException {
        String currentIp;
        String currentHostName;
        Date today = new Date(System.currentTimeMillis());
        try {
            currentIp = InetAddress.getLocalHost().getHostAddress();
            currentHostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            throw new LicenseException(e);
        }
        IpDuration ipDuration = license.getIpDuration(currentIp);
        if (ipDuration == null) {
            ipDuration = license.getIpDuration(currentHostName);
        }
        if (ipDuration.isForever()) {
            logger.info("License is valid forever.");
        } else {
            if (isFirst) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                StringBuilder sb = new StringBuilder("License ");
                sb.append("Start Date: ").append(sdf.format(license.getLicenseStartDate())).append(", ");
                sb.append("Expiry Date: ").append(sdf.format(ipDuration.getExpiryDate()));
                logger.info(sb.toString());
                setIsFirst(false);
            }
            if (today.before(license.getLicenseStartDate())) {
                throw new LicenseException(LicenseException.LICENSE_START_DATE_FAIL, license.getLicenseStartDate().toString(), today.toString());
            } else if (today.after(ipDuration.getExpiryDate())) {
                throw new LicenseException(LicenseException.LICENSE_EXPIRED, ipDuration.getExpiryDate().toString(), today.toString());
            }
        }
    }

    public static void setIsFirst(boolean isFirst) {
        LicenseValidator.isFirst = isFirst;
    }
}
