package com.paybridge.Models.DTOs;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;                        // Successful data
    private String error;                  // Error type (ex: "Bad Request")
    private Map<String, Object> metadata;  // Extra info (optional)
    private String path;                   // Endpoint path
    private LocalDateTime timestamp;       // Time of response

    public ApiResponse(String error, String path, LocalDateTime timestamp) {
        this.error = error;
        this.path = path;
        this.timestamp = timestamp;
    }

    public ApiResponse(String error, LocalDateTime timestamp) {
        this.error = error;
        this.timestamp = timestamp;
    }

    public ApiResponse(boolean success, T data, LocalDateTime timestamp) {
        this.success = success;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, LocalDateTime.now() );
    }


    //For global exception handler
    public static <T> ApiResponse<T> error(String error, String path) {
        return new ApiResponse<>(error, path, LocalDateTime.now());
    }

    //For niche errors across application
    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(error, LocalDateTime.now());
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
