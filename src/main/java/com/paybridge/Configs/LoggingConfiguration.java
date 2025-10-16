package com.paybridge.Configs;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class LoggingConfiguration {

    private final Logger log= LoggerFactory.getLogger(LoggingConfiguration.class);

    @Around("execution(* com.paybridge.Services.*Service.*(..))")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();

        log.debug("Entering {}.{} with args: {}", className, methodName, args);

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            log.info("{}.{} executed in {} ms", className, methodName, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Error in {}.{} after {} ms: {}",
                    className, methodName, duration, e.getMessage(), e);
            throw e;
        }
    }
}
