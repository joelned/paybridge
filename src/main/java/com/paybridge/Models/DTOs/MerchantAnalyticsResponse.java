package com.paybridge.Models.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MerchantAnalyticsResponse {

    private int days;
    private long totalTransactions;
    private long successfulTransactions;
    private long failedTransactions;
    private long pendingTransactions;
    private BigDecimal successRate;
    private BigDecimal totalProcessedAmount;
    private BigDecimal averageTransactionAmount;
    private String primaryCurrency;
    private List<String> currenciesUsed = new ArrayList<>();
    private List<ProviderAnalytics> providers = new ArrayList<>();
    private List<DailyAnalyticsPoint> dailyTrend = new ArrayList<>();

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public long getSuccessfulTransactions() {
        return successfulTransactions;
    }

    public void setSuccessfulTransactions(long successfulTransactions) {
        this.successfulTransactions = successfulTransactions;
    }

    public long getFailedTransactions() {
        return failedTransactions;
    }

    public void setFailedTransactions(long failedTransactions) {
        this.failedTransactions = failedTransactions;
    }

    public long getPendingTransactions() {
        return pendingTransactions;
    }

    public void setPendingTransactions(long pendingTransactions) {
        this.pendingTransactions = pendingTransactions;
    }

    public BigDecimal getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(BigDecimal successRate) {
        this.successRate = successRate;
    }

    public BigDecimal getTotalProcessedAmount() {
        return totalProcessedAmount;
    }

    public void setTotalProcessedAmount(BigDecimal totalProcessedAmount) {
        this.totalProcessedAmount = totalProcessedAmount;
    }

    public BigDecimal getAverageTransactionAmount() {
        return averageTransactionAmount;
    }

    public void setAverageTransactionAmount(BigDecimal averageTransactionAmount) {
        this.averageTransactionAmount = averageTransactionAmount;
    }

    public String getPrimaryCurrency() {
        return primaryCurrency;
    }

    public void setPrimaryCurrency(String primaryCurrency) {
        this.primaryCurrency = primaryCurrency;
    }

    public List<String> getCurrenciesUsed() {
        return currenciesUsed;
    }

    public void setCurrenciesUsed(List<String> currenciesUsed) {
        this.currenciesUsed = currenciesUsed;
    }

    public List<ProviderAnalytics> getProviders() {
        return providers;
    }

    public void setProviders(List<ProviderAnalytics> providers) {
        this.providers = providers;
    }

    public List<DailyAnalyticsPoint> getDailyTrend() {
        return dailyTrend;
    }

    public void setDailyTrend(List<DailyAnalyticsPoint> dailyTrend) {
        this.dailyTrend = dailyTrend;
    }

    public static class ProviderAnalytics {
        private String providerCode;
        private String providerName;
        private long transactions;
        private long successfulTransactions;
        private long failedTransactions;
        private BigDecimal successRate;
        private BigDecimal processedAmount;

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }

        public long getTransactions() {
            return transactions;
        }

        public void setTransactions(long transactions) {
            this.transactions = transactions;
        }

        public long getSuccessfulTransactions() {
            return successfulTransactions;
        }

        public void setSuccessfulTransactions(long successfulTransactions) {
            this.successfulTransactions = successfulTransactions;
        }

        public long getFailedTransactions() {
            return failedTransactions;
        }

        public void setFailedTransactions(long failedTransactions) {
            this.failedTransactions = failedTransactions;
        }

        public BigDecimal getSuccessRate() {
            return successRate;
        }

        public void setSuccessRate(BigDecimal successRate) {
            this.successRate = successRate;
        }

        public BigDecimal getProcessedAmount() {
            return processedAmount;
        }

        public void setProcessedAmount(BigDecimal processedAmount) {
            this.processedAmount = processedAmount;
        }
    }

    public static class DailyAnalyticsPoint {
        private LocalDate date;
        private long transactions;
        private long successfulTransactions;
        private BigDecimal processedAmount;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public long getTransactions() {
            return transactions;
        }

        public void setTransactions(long transactions) {
            this.transactions = transactions;
        }

        public long getSuccessfulTransactions() {
            return successfulTransactions;
        }

        public void setSuccessfulTransactions(long successfulTransactions) {
            this.successfulTransactions = successfulTransactions;
        }

        public BigDecimal getProcessedAmount() {
            return processedAmount;
        }

        public void setProcessedAmount(BigDecimal processedAmount) {
            this.processedAmount = processedAmount;
        }
    }
}

