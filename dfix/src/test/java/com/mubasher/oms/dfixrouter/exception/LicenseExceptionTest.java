package com.mubasher.oms.dfixrouter.exception;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Created by Nilaan L on 7/10/2024.
 */
class LicenseExceptionTest {

    @ParameterizedTest
    @ValueSource(ints = {LicenseException.IP_VALIDATION_FAIL,
            LicenseException.SESSION_VALIDATION_FAIL,
            LicenseException.LICENSE_START_DATE_FAIL,
            LicenseException.LICENSE_EXPIRED,
            LicenseException.ALLOWED_PARALLEL_DFIX_FAIL,
            IConstants.CONSTANT_MINUS_1,
    })
    void getMessage(int errorCode) {
        final LicenseException licenseException = new LicenseException(errorCode, "expected", "current");
        final String message = licenseException.getMessage();
        Assertions.assertFalse(message.isEmpty());
        if (errorCode == IConstants.CONSTANT_MINUS_1) {
            Assertions.assertEquals("Expected: expected, Current value: current" , message );
        }

    }
}
