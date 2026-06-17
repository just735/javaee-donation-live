package com.javaee.donation.viewer.exception;

public class ViewerBusinessException extends RuntimeException {

    private final String code;

    public ViewerBusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
