package com.mubasher.oms.dfixrouter.server.admin.license;

import com.mubasher.oms.dfixrouter.beans.License;
import com.mubasher.oms.dfixrouter.exception.LicenseException;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import com.mubasher.oms.dfixrouter.server.DFIXRouterManager;

import java.util.Date;

public class ExpiryDateChecker implements Runnable {
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.server.admin.license.ExpiryDateChecker");
    private static ExpiryDateChecker instance;
    private static int checkingIntervalInMin = 2;
    private static boolean isFirst;
    private License license;
    private LicenseValidator licenseValidator;


    private ExpiryDateChecker(LicenseValidator licenseValidator, License license) {
        this.licenseValidator = licenseValidator;
        this.license = license;
        setIsFirst(true);
    }

    private static void setIsFirst(boolean isFirst) {
        ExpiryDateChecker.isFirst = isFirst;
    }

    public static ExpiryDateChecker getInstance(LicenseValidator licenseValidator, License license) {
        if (instance == null) {
            instance = new ExpiryDateChecker(licenseValidator, license);
        }
        return instance;
    }

    public static long getCheckingIntervalInMin() {
        return checkingIntervalInMin;
    }

    @Override
    public void run() {
        try {
            logger.info("License Validation: " + new Date());
            if (isFirst) {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                setIsFirst(false);
            }
            licenseValidator.validateExpiryPeriod(license);
        } catch (LicenseException e) {
            logger.error("License Validation Failed: " + e.getMessage(), e);
            DFIXRouterManager.exitApplication(0);
        }
    }

    public License getLicense() {
        return license;
    }
}
