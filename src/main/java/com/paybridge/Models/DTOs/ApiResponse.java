package com.paybridge.Models.DTOs;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ErrorDetail error;
    private Map<String, Object> metadata;
    private String path;
    private LocalDateTime timestamp;

    public ApiResponse(ErrorDetail error, String path, LocalDateTime timestamp) {
        this.success = false;
        this.error = error;
        this.path = path;
        this.timestamp = timestamp;
    }

    public ApiResponse(ErrorDetail error, LocalDateTime timestamp) {
        this.success = false;
        this.error = error;
        this.timestamp = timestamp;
    }

    public ApiResponse(boolean success, T data, LocalDateTime timestamp) {
        this.success = success;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(ErrorDetail error) {
        return new ApiResponse<>(error, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(ErrorDetail error, String path) {
        return new ApiResponse<>(error, path, LocalDateTime.now());
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
