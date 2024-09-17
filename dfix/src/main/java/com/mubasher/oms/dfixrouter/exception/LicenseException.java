package com.mubasher.oms.dfixrouter.exception;

public class LicenseException extends Exception {
    public static final int LICENSE_FILE_CHECKSUM_FAIL = 0;
    public static final int SESSION_VALIDATION_FAIL = 1;
    public static final int IP_VALIDATION_FAIL = 2;
    public static final int LICENSE_START_DATE_FAIL = 3;
    public static final int LICENSE_EXPIRED = 4;
    public static final int ALLOWED_PARALLEL_DFIX_FAIL = 5;

    private final int errorCode;
    private final String expected;
    private final String current;

    public LicenseException(Throwable cause) {
        super(cause);
        this.errorCode = -1;
        this.expected = "";
        this.current = "";
    }

    public LicenseException(int errorCode, String expected, String current) {
        this.errorCode = errorCode;
        this.expected = expected;
        this.current = current;
    }

    public int getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        String errorMessage;
        if (this.expected.trim().length() > 0 && this.current.trim().length() > 0) {
            errorMessage = getErrorMessage(this.expected, this.current);
        } else {
            errorMessage = super.getMessage();
        }
        return errorMessage;
    }

    private String getErrorMessage(String expected, String current) {
        String message;
        switch (this.errorCode) {
            case IP_VALIDATION_FAIL:
                message = "Server Ip, Host Name(" + current + ") is not allowed for this application or It is a Loopback Ip." +
                        " Allowed Ips are: " + expected;
                break;
            case SESSION_VALIDATION_FAIL:
                message = "Number of sessions exceeded expected value. " +
                        getDefaultErrorMessage(expected, current);
                break;
            case LICENSE_START_DATE_FAIL:
                message = "License starts on " + expected;
                break;
            case LICENSE_EXPIRED:
                message = "License expired on " + expected;
                break;
            case ALLOWED_PARALLEL_DFIX_FAIL:
                message = "Number of parallel DFixRouter exceeded expected value. " +
                        getDefaultErrorMessage(expected, current);
                break;
            default:
                message = getDefaultErrorMessage(expected, current);
                break;
        }
        return message;
    }

    private String getDefaultErrorMessage(String expected, String current) {
        return "Expected: " + expected + ", Current value: " + current;
    }
}
