package com.paybridge.Models.DTOs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paybridge.Models.Enums.ApiErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetail {

    private final String message;
    private final ApiErrorCode code;
    private final Object details;

    private ErrorDetail(String message, ApiErrorCode code, Object details) {
        this.message = message;
        this.code = code;
        this.details = details;
    }

    public static ErrorDetail of(String message) {
        return new ErrorDetail(message, ApiErrorCode.INTERNAL_ERROR, null);
    }

    public static ErrorDetail of(String message, ApiErrorCode code) {
        return new ErrorDetail(message, code, null);
    }

    public static ErrorDetail of(String message, ApiErrorCode code, Object details) {
        return new ErrorDetail(message, code, details);
    }

    public String getMessage() {
        return message;
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
