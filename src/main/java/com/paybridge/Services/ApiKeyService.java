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

    private Logger logger = LoggerFactory.getLogger(ApiKeyService.class);

    private static final String REDIS_COUNT_KEY = "apikey:%s:count:%s:%s";
    private static final String REDIS_LOG_KEY = "apikey:%s:log";

    private static final int HOURLY_LIMIT = 1000;
    private static final int DAILY_LIMIT = 10000;

    private static final String TEST_PREFIX = "pk_test_";
    private static final String LIVE_PREFIX = "pk_live_";

    public String generateApiKey(boolean isTestMode){
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        return isTestMode ? TEST_PREFIX : LIVE_PREFIX + key;
    }

    public Optional<Merchant> validateApiKey(String apiKey){
        if(apiKey == null || apiKey.isEmpty()){
            return Optional.empty();
        }

        if(apiKey.startsWith(TEST_PREFIX)){
            return merchantRepository.findByApiKeyTest(apiKey);
        }
        if(apiKey.startsWith(LIVE_PREFIX)){
            return merchantRepository.findByApiKeyLive(apiKey);
        }

        return Optional.empty();
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
    public void logApiKeyUsageToRedis(Merchant merchant, String apiKey, HttpServletRequest request, int responseStatus){
        try{
            incrementUsageCounter(apiKey);
        }catch (Exception ex){
            logger.error("Failed to log API usage to Redis for key: {}",
                    maskApiKey(apiKey), ex);
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

    private void storeDetailedLog(Merchant merchant, String apiKey, HttpServletRequest request,
                                  Integer responseStatus){
        ApiKeyUsage log = new ApiKeyUsage(
                merchant.getId(),
                request.getRequestURI(),
                getClientIpAddress(request),
                responseStatus,
                request.getMethod(),
                request.getHeader("User-Agent")
        );

        String logKey = String.format(REDIS_LOG_KEY, apiKey);
        redisTemplate.opsForList().rightPush(logKey, log);

        redisTemplate.expire(logKey, Duration.ofDays(7));
    }

    public boolean checkRateLimit(String apiKey){
        try{
            LocalDateTime now = LocalDateTime.now();

            String hourlyKey = String.format(REDIS_COUNT_KEY, apiKey, "hourly", now.format(DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd-HH"
            )));

            Integer hourlyCount = (Integer) redisTemplate.opsForValue().get(hourlyKey);

            if(hourlyCount != null && hourlyCount > HOURLY_LIMIT){
                logger.error("Hourly limit exceeded for api key {}", maskApiKey(apiKey));
                return false;
            }

            String dailyKey = String.format(REDIS_COUNT_KEY, apiKey, "daily", now.format(DateTimeFormatter.ofPattern(
                    "yyy-MM-dd"
            )));

            Integer dailyCount = (Integer) redisTemplate.opsForValue().get(dailyKey);

            if(dailyCount != null && dailyCount > DAILY_LIMIT){
                logger.error("Daily limit exceeded for api key {}", apiKey);
                return false;
            }

            return true;
        } catch(Exception ex){
            logger.error("Failed to check rate limit, allowing request", ex);
            return true;
        }
    }

    public Map<String, Object> getRealTimeStatistics(String apiKey){
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> stats = new HashMap<>();

        try{
            String hourlyKey = String.format(REDIS_COUNT_KEY, apiKey, "hourly", now.format(DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd-HH"
            )));

            Integer hourlyCount = (Integer) redisTemplate.opsForValue().get(hourlyKey);
            Integer hourlyRemaining = hourlyCount != null ? HOURLY_LIMIT - hourlyCount : HOURLY_LIMIT;

            stats.put("hourlyCount", hourlyCount);
            stats.put("hourlyLimit", HOURLY_LIMIT);
            stats.put("hourlyRemaining", hourlyRemaining);

            String dailyKey = String.format(REDIS_COUNT_KEY, apiKey, "daily", now.format(DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd"
            )));

            Integer dailyCount = (Integer) redisTemplate.opsForValue().get(dailyKey);
            Integer dailyRemaining = dailyCount != null ? DAILY_LIMIT - dailyCount : DAILY_LIMIT;

            stats.put("dailyCount", dailyCount);
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

            if (logKeys.isEmpty()) {
                logger.debug("No logs found in Redis to persist");
                return;
            }

            int totalPersisted = 0;
            List<ApiKeyUsage> batch = new ArrayList<>();

            for (String logKey : logKeys) {
                try {
                    // Get all logs for this API key
                    List<Object> logs = redisTemplate.opsForList().range(logKey, 0, -1);

                    if (logs == null || logs.isEmpty()) {
                        continue;
                    }

                    // Convert to database entities
                    for (Object logObj : logs) {
                        try {
                            ApiKeyUsage log = objectMapper.convertValue(logObj, ApiKeyUsage.class);

                            // Find merchant (for database foreign key)
                            Optional<Merchant> merchantOpt = merchantRepository.findById(log.getMerchantId());
                            if (merchantOpt.isEmpty()) {
                                logger.warn("Merchant not found for log: {}", log.getMerchantId());
                                continue;
                            }

                            // Create database entity
                            ApiKeyUsage usage = new ApiKeyUsage();
                            usage.setMerchantId(merchantOpt.get().getId());
                            usage.setEndpoint(log.getEndpoint());
                            usage.setMethod(log.getMethod());
                            usage.setIpAddress(log.getIpAddress());
                            usage.setUserAgent(log.getUserAgent());
                            usage.setResponseStatus(log.getResponseStatus());

                            batch.add(usage);

                        } catch (Exception e) {
                            logger.error("Failed to convert log entry", e);
                        }
                    }

                    if (!batch.isEmpty()) {
                        apiKeyUsageRepository.saveAll(batch);
                        totalPersisted += batch.size();
                        batch.clear();
                    }

                    redisTemplate.delete(logKey);

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
