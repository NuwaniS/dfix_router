package com.mubasher.oms.dfixrouter.server.admin.license;

import com.mubasher.oms.dfixrouter.beans.License;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

import java.lang.reflect.Field;

class ExpiryDateCheckerTest {

    @Spy
    License license;
    @Spy
    LicenseValidator licenseValidator;

    @AfterAll
    public static void tearDownClass() throws NoSuchFieldException, IllegalAccessException {
        Field field = ExpiryDateChecker.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null,null);
        field.setAccessible(false);
    }

    @Test
    void getInstanceTest() {
        Assertions.assertNotNull(ExpiryDateChecker.getInstance(licenseValidator, license), "ExpiryDateChecker.getInstance() cannot return null.");
    }

    @Test
    void getCheckingIntervalInMinTest() {
        Assertions.assertTrue(ExpiryDateChecker.getCheckingIntervalInMin() > 0, "System should periodically check the licensing to avoid the After start changes.");
    }

    @Test
    void getLicenseTest() {
        Assertions.assertEquals(license, ExpiryDateChecker.getInstance(licenseValidator, license).getLicense(), "System should periodically check the licensing to avoid the After start changes.");
    }
}
