package com.mubasher.dfix.license;

import com.mubasher.oms.dfixrouter.beans.IpDuration;
import com.mubasher.oms.dfixrouter.beans.License;
import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.exception.LicenseException;
import com.mubasher.oms.dfixrouter.server.admin.license.LicenseValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;
import javax.swing.*;
import javax.swing.table.TableModel;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GenerateButtonListener {
    private static final Logger logger = LogManager.getLogger(GenerateButtonListener.class);
    private JTable jTable;
    private String parallelInstances = "1";
    private String sessionCount;
    private String startDate = null;
    private static GenerateButtonListener generateButtonListener;
    private static final String PRIVATE_KEY_FILE = "./system/DFIXRTR_Private.der";
    public static final String LICENSE_PATH = "./system/LicenseDetails.license";
    public static final String PUBLIC_KEY_PATH = "./system/DFIXRTR_Public.der";
    private PrivateKey privateKey;
    private LicenseValidator licenseValidator = new LicenseValidator();

    private GenerateButtonListener() {
    }

    public static synchronized GenerateButtonListener getGenerateButtonListener() {
        if (generateButtonListener == null){
            generateButtonListener = new GenerateButtonListener();
        }
        return generateButtonListener;
    }

    public void setjTable(JTable jTable) {
        this.jTable = jTable;
    }

    public void setParallelInstances(String parallelInstances) {
        this.parallelInstances = parallelInstances;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public void setSessionCount(String sessionCount) {
        this.sessionCount = sessionCount;
    }

    public void generateLicenseFile() throws LicenseException, IOException, ClassNotFoundException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, DFIXConfigException {
        loadKeys();
        createLicenseFile();
    }

    private void loadKeys() throws LicenseException, IOException, ClassNotFoundException {
        final File licenseFile = new File(PRIVATE_KEY_FILE);
        licenseValidator.validatePublicKey(PUBLIC_KEY_PATH);
        PublicKey publicKey;
        try(
                ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(licenseFile));
                ObjectInputStream inputStream1 = new ObjectInputStream(new FileInputStream(PUBLIC_KEY_PATH));
                ) {
            privateKey = (PrivateKey) inputStream.readObject();
            publicKey = (PublicKey) inputStream1.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Error loading keys " , e.getMessage());
            throw e;
        }

        if (privateKey == null) {
            throw new LicenseException(-1, "Private key is not available.","Private key is not available.");
        }
        if (publicKey == null) {
            throw new LicenseException(-1,"Public key is not available.","Public key is not available.");
        }

        if (!privateKey.getAlgorithm().equals(publicKey.getAlgorithm())){
            throw new LicenseException(-1,"Private and Public Keys are not in Same Algorithm","Private and Public Keys are not in Same Algorithm");
        }
    }

    private void createLicenseFile() throws LicenseException, DFIXConfigException, IllegalBlockSizeException, NoSuchPaddingException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        final File file = new File(LICENSE_PATH);
        if (file.exists() && !file.delete()){
            throw new LicenseException(new Exception("Current License file cannot be deleted."));
        }
        final Date licenseStartDate = validateLicenseStartDate(startDate);
        final byte validatedSessionCount = validateInputBytes(sessionCount, -1, DataCollector.getSesCountJTextFieldName());
        final byte allowedMaxParallelDfix = validateInputBytes(parallelInstances, -1, DataCollector.getParJTextFieldName());
        License license = new License(licenseStartDate.getTime());
        license.setAllowedSessions(validatedSessionCount);
        license.setAllowedParallelDfixes(allowedMaxParallelDfix);
        license.setIpDurations(validateJTableData(licenseStartDate));
        writeLicenseFile(LICENSE_PATH, license);
        if (!file.exists()) {
            throw new LicenseException(new Exception("License file Creation failed"));
        }
        license = licenseValidator.loadLicenseDetails(PUBLIC_KEY_PATH, LICENSE_PATH);
        final TableModel tableModel = jTable.getModel();
        for (int rowId = 0; rowId < tableModel.getRowCount(); rowId++) {
            final String inputIp = (String)tableModel.getValueAt(rowId, 1);
            if (!license.getAllowedIPs().contains(inputIp)){
                throw new LicenseException(new Exception("License File is not created with All the inserted IPs"));
            }
        }
        if (validatedSessionCount != license.getAllowedSessions()) {
            throw new LicenseException(new Exception("License File is not created with inserted Sessions Count"));
        }
        logger.info("{} created", license);
    }

    private HashMap<String, IpDuration> validateJTableData(Date licenseStartDate) throws DFIXConfigException {
        final HashMap<String, IpDuration> ipDurations = new LinkedHashMap<>();
        final Calendar cal = Calendar.getInstance();
        cal.setTime(licenseStartDate);
        final TableModel tableModel = jTable.getModel();
        for (int rowId = 0; rowId < tableModel.getRowCount(); rowId++) {
            final String validatedIP = (String)tableModel.getValueAt(rowId, 1);
            if (validatedIP == null || validatedIP.trim().isEmpty()) {
                throw new DFIXConfigException("IP is not valid in Row: " + rowId);
            }
            IpDuration ipDuration = new IpDuration(validatedIP);
            final String duration = (String)tableModel.getValueAt(rowId, 2);
            if (duration != null && !IConstants.LICENSE_FOREVER.equals(duration)) {
                final int months = validateInputDurations(duration, rowId);
                cal.add(Calendar.MONTH, months);
                ipDuration.setExpiryDate(cal.getTimeInMillis());
            } else {
                logger.info("Unlimited license for {}", validatedIP);
                ipDuration.setForever(true);
            }
            ipDurations.put(validatedIP, ipDuration);
        }
        return ipDurations;
    }

    private byte validateInputBytes(String byteString, int rowId, String part) throws DFIXConfigException {
        String exMessage;
        String suffixForRowId = "Please check should be passed in Row: " + rowId;
        if (byteString == null) {
            exMessage = "Allowed Number of " + part + " should be passed";
            if (rowId >= 0) {
                exMessage += suffixForRowId;
            }
            throw new DFIXConfigException(exMessage);
        }
        byte byteValue = Byte.parseByte(byteString);
        if (Integer.parseInt(byteString) > Byte.MAX_VALUE){
            exMessage = "Maximum Allowed " + part + " " + Byte.MAX_VALUE;
            if (rowId >= 0) {
                exMessage += suffixForRowId;
            }
            throw new DFIXConfigException(exMessage);
        }

        if (byteValue <= 0) {
            exMessage = "Passed " + part + " should be a greater than Zero.";
            if (rowId >= 0) {
                exMessage += suffixForRowId;
            }
            throw new DFIXConfigException(exMessage);
        }
        return byteValue;
    }

    private static Date validateLicenseStartDate(String property) throws DFIXConfigException {
        Date licensStartDate;
        if (property == null || property.trim().length() == 0){
            licensStartDate = new Date(System.currentTimeMillis());
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat(DataCollector.getDateFormat());
            try {
                licensStartDate = sdf.parse(property);
            } catch (ParseException e) {
                throw new DFIXConfigException("Date Format should be " + DataCollector.getDateFormat());
            }
        }
        if (licensStartDate == null) {
            throw new DFIXConfigException("License Start Date not valid.");
        }
        return licensStartDate;
    }

    private int validateInputDurations(String duration, int rowId) throws DFIXConfigException{
        int months = -1;
        if (duration.trim().length() > 0 && !duration.trim().equals(IConstants.LICENSE_FOREVER)){
            try {
                months = Integer.parseInt(duration);
            } catch (NumberFormatException e) {
                throw new DFIXConfigException("Invalid duration entered. Please check should be passed in Row: " + rowId);
            }
            if (months < 0){
                throw new DFIXConfigException("Duration cannot be Negative. Please check should be passed in Row: " + rowId);
            }
        }
        return months;
    }

    private void writeLicenseFile(String fileLocation, License license) throws IOException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, DFIXConfigException {
        Cipher cipher = Cipher.getInstance(privateKey.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        checkAgainstCipher(license, cipher);
        final SealedObject sealedObject = new SealedObject(license, cipher);
        try(ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(fileLocation))) {
            objectOutputStream.writeObject(sealedObject);
        }

    }

    private void checkAgainstCipher(License license, Cipher cipher) throws IOException, DFIXConfigException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(license);
        out.flush();
        int objectsize = baos.toByteArray().length - 4;
        if (objectsize > cipher.getOutputSize(objectsize)){
            int keySize = ((RSAKey)privateKey).getModulus().bitLength();
            int padSize = 11;
            int maxSize = (keySize / 8) - padSize;
            throw new DFIXConfigException("Key Size can only hold up to " +  maxSize + " But, Object Size is " + objectsize);
        }
    }
}
