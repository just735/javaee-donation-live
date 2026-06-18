package com.javaee.donation.analytics.exception;

public class AnalyticsBusinessException extends RuntimeException {

    private final String code;

    public AnalyticsBusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
