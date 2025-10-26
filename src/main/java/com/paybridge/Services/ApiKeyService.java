package com.paybridge.Services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paybridge.Models.Entities.ApiKeyUsage;
import com.paybridge.Models.Entities.Merchant;
import com.paybridge.Repositories.ApiKeyUsageRepository;
import com.paybridge.Repositories.MerchantRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ApiKeyService {

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private ApiKeyUsageRepository apiKeyUsageRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final Logger logger = LoggerFactory.getLogger(ApiKeyService.class);

    private static final String REDIS_COUNT_KEY = "apikey:%s:count:%s:%s";
    private static final String REDIS_LOG_KEY = "apikey:%s:logs";

    private static final int HOURLY_LIMIT = 1000;
    private static final int DAILY_LIMIT = 10000;

    private static final String TEST_PREFIX = "pk_test_";
    private static final String LIVE_PREFIX = "pk_live_";

    public String generateApiKey(boolean isTestMode){
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        return (isTestMode ? TEST_PREFIX : LIVE_PREFIX ) + key;
    }

    public boolean isTestMode(String apiKey){
        return apiKey != null && apiKey.startsWith(TEST_PREFIX);
    }

    @Transactional
    public void regenerateApiKey(Long merchantId, boolean regenerateTest, boolean regenerateLive){
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(()-> new IllegalArgumentException("Merchant not found"));

        if(regenerateTest){
            merchant.setApiKeyTest(generateApiKey(true));
        }

        if(regenerateLive){
            merchant.setApiKeyLive(generateApiKey(false));
        }
        merchantRepository.save(merchant);
    }

    @Async
    public void logApiKeyUsageToRedis(
            Merchant merchant,
            String apiKey,
            String requestUri,
            String httpMethod,
            String clientIp,
            String requestHeader,
            int responseStatus) {

        try {
            incrementUsageCounter(apiKey);
            storeDetailedLog(merchant, apiKey, requestUri, httpMethod, clientIp, requestHeader, responseStatus);
        } catch (Exception ex) {
            logger.error("Failed to log API usage to Redis for key: {}", maskApiKey(apiKey), ex);
        }
    }

    private void incrementUsageCounter(String apiKey){
        LocalDateTime now = LocalDateTime.now();

        String hourlyKey = String.format(REDIS_COUNT_KEY, apiKey, "hourly", now.format
                (DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")));

        redisTemplate.opsForValue().increment(hourlyKey);
        redisTemplate.expire(hourlyKey, Duration.ofHours(2));

        String dailyKey = String.format(REDIS_COUNT_KEY, apiKey, "daily", now.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        ));

        redisTemplate.opsForValue().increment(dailyKey);
        redisTemplate.expire(dailyKey, Duration.ofDays(2));
    }

    private void storeDetailedLog(Merchant merchant, String apiKey,String requestURI,
                                  String httpMethod, String clientIp, String requestHeader,
                                  Integer responseStatus){
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("merchantId", merchant.getId());
            logData.put("endpoint", requestURI);
            logData.put("ipAddress", clientIp);
            logData.put("responseStatus", responseStatus);
            logData.put("method", httpMethod);
            logData.put("userAgent", requestHeader);
            logData.put("timestamp", LocalDateTime.now().toString());

            String logKey = String.format(REDIS_LOG_KEY, apiKey);
            redisTemplate.opsForList().rightPush(logKey, logData);
            redisTemplate.expire(logKey, Duration.ofDays(7));

            logger.debug("Stored log to Redis key: {}", logKey);
        } catch (Exception e) {
            logger.error("Failed to store detailed log to Redis", e);
        }
    }

    public boolean checkRateLimit(String apiKey){
        try{
            LocalDateTime now = LocalDateTime.now();

            String hourlyKey = String.format(REDIS_COUNT_KEY, apiKey, "hourly", now.format(DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd-HH"
            )));

            Long hourlyCount = convertToLong(redisTemplate.opsForValue().get(hourlyKey));

            if(hourlyCount != null && hourlyCount > HOURLY_LIMIT){
                logger.warn("Hourly limit exceeded for api key {}", maskApiKey(apiKey));
                return false;
            }

            String dailyKey = String.format(REDIS_COUNT_KEY, apiKey, "daily", now.format(DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd"
            )));

            Long dailyCount = convertToLong(redisTemplate.opsForValue().get(dailyKey));

            if(dailyCount != null && dailyCount > DAILY_LIMIT){
                logger.warn("Daily limit exceeded for api key {}", maskApiKey(apiKey));
                return false;
            }

            return true;
        } catch(Exception ex){
            logger.error("Failed to check rate limit, allowing request", ex);
            return true;
        }
    }

    public Long convertToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    public Map<String, Object> getRealTimeStatistics(String apiKey){
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> stats = new HashMap<>();

        try{
            String hourlyKey = String.format(REDIS_COUNT_KEY, apiKey, "hourly", now.format(DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd-HH"
            )));

            // FIX: Changed from Integer to Long
            Long hourlyCount = (Long) redisTemplate.opsForValue().get(hourlyKey);
            Long hourlyRemaining = hourlyCount != null ? HOURLY_LIMIT - hourlyCount : HOURLY_LIMIT;

            stats.put("hourlyCount", hourlyCount != null ? hourlyCount : 0);
            stats.put("hourlyLimit", HOURLY_LIMIT);
            stats.put("hourlyRemaining", hourlyRemaining);

            String dailyKey = String.format(REDIS_COUNT_KEY, apiKey, "daily", now.format(DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd"
            )));

            // FIX: Changed from Integer to Long
            Long dailyCount = (Long) redisTemplate.opsForValue().get(dailyKey);
            Long dailyRemaining = dailyCount != null ? DAILY_LIMIT - dailyCount : DAILY_LIMIT;

            stats.put("dailyCount", dailyCount != null ? dailyCount : 0);
            stats.put("dailyLimit", DAILY_LIMIT);
            stats.put("dailyRemaining", dailyRemaining);

        }catch (Exception ex){
            logger.error("Failed to fetch usage statistics ", ex);
            stats.put("error", "Failed to fetch statistics");
        }
        return stats;
    }

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void persistLogsToDatabase() {
        logger.info("Starting scheduled job: persisting Redis logs to database");

        try {
            Set<String> logKeys = redisTemplate.keys("apikey:*:logs");

            if (logKeys == null || logKeys.isEmpty()) {
                logger.debug("No logs found in Redis to persist");
                return;
            }

            int totalPersisted = 0;
            List<ApiKeyUsage> batch = new ArrayList<>();

            for (String logKey : logKeys) {
                try {
                    List<Object> logs = redisTemplate.opsForList().range(logKey, 0, -1);

                    if (logs == null || logs.isEmpty()) {
                        continue;
                    }

                    for (Object logObj : logs) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> logMap = (Map<String, Object>) logObj;

                            Long merchantId = logMap.get("merchantId") instanceof Integer
                                    ? ((Integer) logMap.get("merchantId")).longValue()
                                    : (Long) logMap.get("merchantId");

                            Optional<Merchant> merchantOpt = merchantRepository.findById(merchantId);
                            if (merchantOpt.isEmpty()) {
                                logger.warn("Merchant not found for log: {}", merchantId);
                                continue;
                            }

                            ApiKeyUsage usage = new ApiKeyUsage();
                            usage.setMerchantId(merchantId);
                            usage.setEndpoint((String) logMap.get("endpoint"));
                            usage.setMethod((String) logMap.get("method"));
                            usage.setIpAddress((String) logMap.get("ipAddress"));
                            usage.setUserAgent((String) logMap.get("userAgent"));

                            Object responseStatus = logMap.get("responseStatus");
                            usage.setResponseStatus(responseStatus instanceof Integer
                                    ? (Integer) responseStatus
                                    : Integer.parseInt(responseStatus.toString()));

                            batch.add(usage);

                        } catch (Exception e) {
                            logger.error("Failed to convert log entry: {}", e.getMessage(), e);
                        }
                    }

                    if (!batch.isEmpty()) {
                        apiKeyUsageRepository.saveAll(batch);
                        totalPersisted += batch.size();
                        batch.clear();
                    }

                    redisTemplate.delete(logKey);
                    logger.debug("Deleted Redis key after processing: {}", logKey);

                } catch (Exception e) {
                    logger.error("Failed to process logs for key: {}", logKey, e);
                }
            }

            logger.info("Successfully persisted {} log entries to database", totalPersisted);

        } catch (Exception e) {
            logger.error("Failed to persist logs to database", e);
        }
    }

    private String maskApiKey(String apiKey){
        if(apiKey == null || apiKey.length() < 16){
            return "***";
        }
        String prefix = apiKey.substring(0,11);
        String suffix = apiKey.substring(apiKey.length() - 3);
        return prefix + "..." + suffix;
    }

    public String getClientIpAddress(HttpServletRequest request){
        String XForwardedFor = request.getHeader("X-Forwarded-For");

        if (XForwardedFor != null && !XForwardedFor.isEmpty()) {
            return XForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}